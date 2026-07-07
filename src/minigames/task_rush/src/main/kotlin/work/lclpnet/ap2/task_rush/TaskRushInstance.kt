package work.lclpnet.ap2.task_rush

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.numbers.StyledFormat
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.scores.DisplaySlot
import work.lclpnet.ap2.ext.allPlayers
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.util.*
import work.lclpnet.ap2.task_rush.task.TaskManager
import work.lclpnet.game.impl.prot.ProtectionTypes
import work.lclpnet.game.util.ResetWorldModifier

class TaskRushInstance(
    override val gameHandle: MiniGameHandle,
    override val level: ServerLevel,
    val walls: ResetWorldModifier,
) : MiniGameInstance {

    val data = useDataContainer(::IntScoreDataContainer)
    override val winManager = useFFAWinManager(map = null) { data }
    override val participantListener = useLastRemainingParticipantListener(winManager)
    val taskManager = TaskManager(gameHandle, level, data) {
        winManager.complete()
    }

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

        setupObjective()

        useStartup(::go)
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

        taskManager.nextTask(initial = true)
    }
}
