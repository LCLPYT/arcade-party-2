package work.lclpnet.ap2.task_rush

import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.numbers.StyledFormat
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.gamerules.GameRules
import net.minecraft.world.scores.DisplaySlot
import work.lclpnet.ap2.ext.allPlayers
import work.lclpnet.ap2.ext.hooks
import work.lclpnet.ap2.ext.isParticipating
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.ext.players
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.util.*
import work.lclpnet.ap2.impl.util.world.ChunkPersistence
import work.lclpnet.ap2.task_rush.task.TaskManager
import work.lclpnet.ap2.util.useGameRules
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.game.util.ResetWorldModifier
import work.lclpnet.kibu.hook.entity.ServerPlayerHooks
import work.lclpnet.kibu.hook.level.BlockModificationHooks

class TaskRushInstance(
    override val gameHandle: MiniGameHandle,
    override val level: ServerLevel,
    val walls: ResetWorldModifier,
) : MiniGameInstance {

    val data = useDataContainer(::IntScoreDataContainer)
    override val winManager = useFFAWinManager(map = null) { data }
    override val participantListener = useLastRemainingParticipantListener(winManager)
    val taskManager = TaskManager(gameHandle, level, data, ChunkPersistence(level, gameHandle)) {
        winManager.complete()
    }
    val blocksPlacedByPlayers = HashSet<BlockPos>()

    init {
        useSurvivalMode()

        gameHandle.whenDone {
            taskManager.unload()
        }
    }

    override fun start() {
        configureDefaults()

        for (player in allPlayers()) {
            gameHandle.worldFacade.teleport(player)
        }

        for (player in players()) {
            giveEffects(player)
        }

        setupObjective()

        useGameRules {
            set(GameRules.KEEP_INVENTORY, true)
        }

        useStartup(::go)
    }

    private fun giveEffects(player: ServerPlayer) {
        player.addEffect(MobEffectInstance(MobEffects.SPEED, -1, 1, false, false, false))
        player.addEffect(MobEffectInstance(MobEffects.HASTE, -1, 1, false, false, false))
    }

    private fun setupObjective() {
        val objective = gameHandle.scoreboardManager.translateObjective("score", "ap2.score")
            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)

        objective.setSlot(DisplaySlot.LIST)
        objective.setNumberFormat(StyledFormat.PLAYER_LIST_DEFAULT)

        useScoreboardStatsSync(data, objective)

        for (player in allPlayers()) {
            objective.add(player)
        }
    }

    fun go() {
        walls.undo()

        useProtector {
            allowAll()

            ProtectionTypes.ALLOW_DAMAGE.disallow(this) { victim, source ->
                (victim is ServerPlayer && source.entity is ServerPlayer) || source.isOf(DamageTypes.FALL)
            }

            disallow(ProtectionTypes.HUNGER)
        }

        taskManager.init()

        SetTaskCommand(taskManager).register(gameHandle.commands)
        SkipTaskCommand(taskManager).register(gameHandle.commands)

        ServerPlayerHooks.AFTER_RESPAWN.registerWith(hooks) { player, _, _ ->
            if (isParticipating(player)) {
                giveEffects(player)
            }
        }

        BlockModificationHooks.BLOCK_PLACED.registerWith(hooks) { level, pos, entity ->
            if (level == this.level && entity is ServerPlayer && isParticipating(entity)) {
                blocksPlacedByPlayers.add(pos.immutable())
            }
        }

        // make block drop twice as much by dropping resources an additional time
        BlockModificationHooks.BREAK_BLOCK.registerWith(hooks) { level, pos, entity ->
            if (level != this.level || entity !is ServerPlayer || !isParticipating(entity)) return@registerWith false

            // don't apply for player placed blocks
            if (blocksPlacedByPlayers.remove(pos)) return@registerWith false

            val state = level.getBlockState(pos)

            if (!entity.hasCorrectToolForDrops(state)) return@registerWith false

            val blockEntity = level.getBlockEntity(pos)
            val tool = entity.inventory.selectedItem

            Block.dropResources(state, level, pos, blockEntity, entity, tool)

            false
        }

        taskManager.nextTask(initial = true)
    }
}
