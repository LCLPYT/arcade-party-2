package work.lclpnet.ap2.game.maniac_digger

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
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.map.MapBootstrapFunction
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.ext.mc.setAttribute
import work.lclpnet.ap2.ext.mc.unbreakable
import work.lclpnet.ap2.ext.runEveryTick
import work.lclpnet.ap2.game.maniac_digger.data.MdGenerator
import work.lclpnet.ap2.game.maniac_digger.data.MdPipe
import work.lclpnet.ap2.impl.game.FFAGameInstance
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
import java.util.*

class ManiacDiggerInstance(gameHandle: MiniGameHandle) : FFAGameInstance(gameHandle), MapBootstrapFunction {

    private val reachedBottom = OrderedDataContainer(PlayerRef::create)
    private val score = IntScoreDataContainer(PlayerRef::create, Ordering.ASCENDING, "ap2.score.blocks_away")
    private val data = CombinedDataContainer(listOf(reachedBottom, score))
    private val pipes = HashMap<UUID, MdPipe>()
    private val wrongTool = HashSet<UUID>()
    private var winHeight = 64

    init {
        useSurvivalMode()
    }

    override fun getData() = data

    override fun bootstrapWorld(world: ServerLevel, map: GameMap) {
        val winHeight: Number = map.requireProperty("goal-height")
        this.winHeight = winHeight.toInt()

        val generator = MdGenerator(world, map, gameHandle.logger, Random())
        val participants = gameHandle.participants
        val pipes = generator.generate(participants.count())

        var i = 0
        for (player in participants) {
            val pipe = pipes[i++]
            this.pipes[player.uuid] = pipe
        }
    }

    override fun prepare() {
        val world = this.world

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
    }

    override fun go() {
        gameHandle.protect { config ->
            config.allow(ProtectionTypes.BREAK_BLOCKS, ProtectionTypes.MODIFY_INVENTORY)
        }

        BlockModificationHooks.BREAK_BLOCK.registerWith(hooks) { _, pos, entity ->
            entity !is ServerPlayer || !canBreak(entity, pos)
        }

        PlayerInteractionHooks.ATTACK_BLOCK.registerWith(hooks) { player, _, _, pos, _ ->
            if (player is ServerPlayer && canBreak(player, pos)) {
                onHitBlock(player, pos)
            }
            InteractionResult.PASS
        }

        runEveryTick {
            checkGoal()
        }
    }

    private fun canBreak(player: ServerPlayer, pos: BlockPos): Boolean {
        if (!gameHandle.participants.isParticipating(player) || winManager.isGameOver) {
            return false
        }

        val pipe = pipes[player.uuid] ?: return false

        if (!pipe.bounds.contains(pos)) return false

        val state = player.level().getBlockState(pos)

        return state.block !is StainedGlassBlock && !state.isOf(Blocks.GLASS)
    }

    private fun checkGoal() {
        if (winManager.isGameOver) return

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
        val msg = gameHandle.translations.translateText(player, "game.ap2.maniac_digger.wrong_tool")
            .styled { style -> style.withColor(0xff0000) }

        player.sendOverlayMessage(msg)
        WorldBorderUtil.setWarning(player)
    }

    private fun onCorrectTool(player: ServerPlayer) {
        player.sendOverlayMessage(Component.empty())
        WorldBorderUtil.resetWarningBlocks(player)
    }

    private fun gradePlayers(winnerUuid: UUID?) {
        for (player in gameHandle.participants) {
            if (player.uuid == winnerUuid) continue

            val distance = maxOf(0, player.blockY - winHeight - 1)
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
