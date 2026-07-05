package work.lclpnet.ap2.task_rush

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.ext.allPlayers
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
    val taskManager = TaskManager(gameHandle, data) {
        winManager.complete()
    }

    init {
        useSurvivalMode()
    }

    override fun start() {
        configureDefaults()

        for (player in allPlayers()) {
            gameHandle.worldFacade.teleport(player)
        }

        useStartup(::go)
    }

    fun go() {
        walls.undo()

        useProtector {
            allowAll()

            ProtectionTypes.ALLOW_DAMAGE.disallow(this) { victim, source ->
                victim is ServerPlayer && source.entity is ServerPlayer
            }
        }

        taskManager.nextTask()
    }
}
