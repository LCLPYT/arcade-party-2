package work.lclpnet.ap2.game.maniac_digger

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.StainedGlassBlock
import net.minecraft.world.level.block.state.BlockState
import work.lclpnet.ap2.api.stats.Stat
import work.lclpnet.ap2.ext.hooks
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.ext.mc.setAttribute
import work.lclpnet.ap2.ext.mc.unbreakable
import work.lclpnet.ap2.ext.runEveryTick
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.base.FFAGameInstance
import work.lclpnet.ap2.game.maniac_digger.data.MdPipe
import work.lclpnet.ap2.game.util.useTaskDisplay
import work.lclpnet.ap2.impl.game.data.CombinedDataContainer
import work.lclpnet.ap2.impl.game.data.IntScoreDataContainer
import work.lclpnet.ap2.impl.game.data.OrderedDataContainer
import work.lclpnet.ap2.impl.game.data.Ordering
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.ap2.impl.util.world.WorldBorderUtil
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import work.lclpnet.kibu.hook.level.BlockModificationHooks
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks
import java.util.*
import kotlin.math.roundToInt

private const val DEBUG_GRADING = false

private val BlocksBroken = Stat("blocks_broken", 0)
private val ToolSwitches = Stat("tool_switches", 0)
private val WrongToolsSelected = Stat("wrong_tools_selected", 0)
private val WrongToolsUsed = Stat("wrong_tools_used", 0)
private val CorrectToolStreak = Stat("correct_tool_streak", 0)

class ManiacDiggerInstance(
    gameHandle: MiniGameHandle,
    level: ServerLevel,
    map: GameMap,
    private val winHeight: Int,
    private val pipes: Map<UUID, MdPipe>,
) : FFAGameInstance(gameHandle, level, map) {

    private val reachedBottom = OrderedDataContainer(PlayerRef::create)
    private val score = IntScoreDataContainer(PlayerRef::create, Ordering.ASCENDING, "ap2.score.blocks_away")
    override val data = CombinedDataContainer(listOf(reachedBottom, score))
    private val wrongTool = HashSet<UUID>()
    private val correctToolStreak = Object2IntOpenHashMap<UUID>()
    private val stats = createStats(score, BlocksBroken, ToolSwitches, WrongToolsSelected, WrongToolsUsed, CorrectToolStreak)

    init {
        useSurvivalMode()
    }

    override fun prepare() {
        val world = this.level

        for (player in gameHandle.participants) {
            val pipe = pipes[player.uuid] ?: continue

            val spawn = pipe.spawn
            player.teleportTo(world, spawn.x, spawn.y, spawn.z, emptySet(), 0f, 0f, true)
            player.setAttribute(Attributes.SCALE, 0.5)

            giveItems(player)
        }

        useTaskDisplay()

        data.clear()
        gradePlayers(null)

        if (DEBUG_GRADING) {
            renderGradingDebug()
        }
    }

    private fun renderGradingDebug() {
        commons().debugController().renderer().ifPresent { renderer ->
            for (pipe in pipes.values) {
                val waypoints = pipe.path.waypoints
                for (i in 0 until waypoints.size - 1) {
                    renderer.line(waypoints[i], waypoints[i + 1], 0.1, Blocks.LIME_CONCRETE.defaultBlockState())
                }

                val seen = HashSet<BlockPos>()

                for (box in pipe.interior) {
                    for (pos in box) {
                        if (!seen.add(pos.immutable())) continue

                        val center = pos.center
                        val score = pipe.path.progressToGoal(center).roundToInt()
                        renderer.text(center, Component.literal(score.toString()))
                    }
                }
            }
        }
    }

    override fun go() {
        gameHandle.protect { config ->
            config.allow(ProtectionTypes.BREAK_BLOCKS, ProtectionTypes.MODIFY_INVENTORY)
        }

        BlockModificationHooks.BREAK_BLOCK.registerWith(hooks) { _, pos, entity ->
            if (entity !is ServerPlayer || !canBreak(entity, pos)) {
                return@registerWith true
            }

            onBreakBlock(entity, pos)
            false
        }

        PlayerInteractionHooks.ATTACK_BLOCK.registerWith(hooks) { player, _, _, pos, _ ->
            if (player is ServerPlayer && canBreak(player, pos)) {
                onHitBlock(player, pos)
            }
            InteractionResult.PASS
        }

        PlayerInventoryHooks.SLOT_CHANGE.registerWith(hooks) { player, slot ->
            if (!gameHandle.participants.isParticipating(player) || winManager.gameOver) {
                return@registerWith
            }

            val stack = player.inventory.getItem(slot)

            if (!stack.isEmpty) {
                stats.increment(player, ToolSwitches)
            }
        }

        runEveryTick {
            checkGoal()
        }
    }

    private fun canBreak(player: ServerPlayer, pos: BlockPos): Boolean {
        if (!gameHandle.participants.isParticipating(player) || winManager.gameOver) {
            return false
        }

        val pipe = pipes[player.uuid] ?: return false

        if (!pipe.bounds.contains(pos)) return false

        val state = player.level().getBlockState(pos)

        return state.block !is StainedGlassBlock && !state.isOf(Blocks.GLASS)
    }

    private fun checkGoal() {
        if (winManager.gameOver) return

        for (player in gameHandle.participants) {
            if (player.blockY <= winHeight) {
                reachedBottom.add(player)
                gradePlayers(player.uuid)
                winManager.complete()
                break
            }
        }
    }

    private fun giveItems(player: ServerPlayer) {
        player.inventory.setItem(0, ItemStack(Items.IRON_AXE).unbreakable())
        player.inventory.setItem(1, ItemStack(Items.IRON_PICKAXE).unbreakable())
        player.inventory.setItem(2, ItemStack(Items.IRON_SHOVEL).unbreakable())
        player.inventory.setItem(3, ItemStack(Items.IRON_HOE).unbreakable())
    }

    private fun onHitBlock(player: ServerPlayer, pos: BlockPos) {
        val world = player.level()
        val state = world.getBlockState(pos)
        val stack = player.mainHandItem

        if (isCorrectTool(state, stack)) {
            if (wrongTool.remove(player.uuid)) {
                onCorrectTool(player)
            }
        } else {
            if (wrongTool.add(player.uuid)) {
                onWrongTool(player)
            }
        }
    }

    private fun onWrongTool(player: ServerPlayer) {
        stats.increment(player, WrongToolsSelected)
        correctToolStreak.removeInt(player.uuid)

        val msg = gameHandle.translations.translateText(player, "game.ap2.maniac_digger.wrong_tool")
            .styled { style -> style.withColor(0xff0000) }

        player.sendOverlayMessage(msg)
        WorldBorderUtil.setWarning(player)
    }

    private fun onBreakBlock(player: ServerPlayer, pos: BlockPos) {
        stats.increment(player, BlocksBroken)

        val state = player.level().getBlockState(pos)
        val stack = player.mainHandItem

        if (isCorrectTool(state, stack)) {
            val streak = correctToolStreak.addTo(player.uuid, 1) + 1
            stats.set(player, CorrectToolStreak, maxOf(stats.get(player, CorrectToolStreak), streak))
        } else {
            stats.increment(player, WrongToolsUsed)
            correctToolStreak.removeInt(player.uuid)
        }
    }

    private fun onCorrectTool(player: ServerPlayer) {
        player.sendOverlayMessage(Component.empty())
        WorldBorderUtil.resetWarningBlocks(player)
    }

    private fun gradePlayers(winnerUuid: UUID?) {
        for (player in gameHandle.participants) {
            if (player.uuid == winnerUuid) continue

            val pipe = pipes[player.uuid] ?: continue
            val distance = maxOf(0, pipe.path.progressToGoal(player.position()).roundToInt())
            score.setScore(player, distance)
        }
    }

    private fun isCorrectTool(state: BlockState, stack: ItemStack): Boolean {
        val tool = stack.get(DataComponents.TOOL) ?: return false

        for (rule in tool.rules()) {
            if (state.`is`(rule.blocks())) {
                return true
            }
        }

        return false
    }
}
