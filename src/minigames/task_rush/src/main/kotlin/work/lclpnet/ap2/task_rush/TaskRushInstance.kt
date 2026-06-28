package work.lclpnet.ap2.task_rush

import net.minecraft.server.level.ServerLevel
import work.lclpnet.ap2.ext.allPlayers
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.util.*
import work.lclpnet.game.util.ResetWorldModifier

class TaskRushInstance(
    override val gameHandle: MiniGameHandle,
    override val level: ServerLevel,
    val walls: ResetWorldModifier,
) : MiniGameInstance {

    val data = useDataContainer(::IntScoreDataContainer)
    override val winManager = useFFAWinManager(map = null) { data }
    override val participantListener = useLastRemainingParticipantListener(winManager)

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
    }
}
