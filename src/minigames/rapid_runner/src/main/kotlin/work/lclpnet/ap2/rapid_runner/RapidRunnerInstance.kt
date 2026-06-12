package work.lclpnet.ap2.rapid_runner

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.ext.allPlayers
import work.lclpnet.ap2.ext.players
import work.lclpnet.ap2.ext.runEveryTick
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.player.ParticipantListener
import work.lclpnet.ap2.game.util.configureDefaults
import work.lclpnet.ap2.game.util.useFFAWinManager
import work.lclpnet.ap2.game.util.useStartup
import work.lclpnet.ap2.game.util.useTaskTimer
import work.lclpnet.ap2.impl.game.data.DoubleScoreDataContainer
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import work.lclpnet.game.util.ResetWorldModifier
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val DURATION = 1.minutes + 20.seconds

class RapidRunnerInstance(
    override val gameHandle: MiniGameHandle,
    override val level: ServerLevel,
    val walls: ResetWorldModifier,
) : MiniGameInstance {

    val data = DoubleScoreDataContainer(PlayerRef::create)
    val winManager = useFFAWinManager(null) { data }

    override fun start() {
        configureDefaults()

        for (player in allPlayers()) {
            gameHandle.worldFacade.teleport(player)
        }

        useStartup(::go)
    }

    private fun go() {
        walls.undo()

        val spawn = level.respawnData.pos().center

        runEveryTick {
            for (player in players()) {
                val dist = player.position().subtract(spawn).horizontalDistance()
                data.setScore(player, dist)
            }
        }

        useTaskTimer(DURATION).whenDone {
            winManager.complete()
        }
    }

    override val participantListener = object : ParticipantListener {
        override fun participantRemoved(player: ServerPlayer) {
            winManager.checkForLastRemaining()
        }
    }
}