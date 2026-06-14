package work.lclpnet.ap2.rapid_runner

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.api.stats.CommonStats
import work.lclpnet.ap2.ext.allPlayers
import work.lclpnet.ap2.ext.players
import work.lclpnet.ap2.ext.runEveryTick
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.data.DoubleScoreDataContainer
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.ap2.game.util.*
import work.lclpnet.game.impl.prot.ProtectionTypes
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
    val stats = createFFAStats(winManager, data, CommonStats.DoubleScore, listOf())
    override val participantListener = useLastRemainingParticipantListener(winManager)

    override fun start() {
        useSurvivalMode()
        configureDefaults()

        for (player in allPlayers()) {
            gameHandle.worldFacade.teleport(player)
        }

        useStartup(::go)
    }

    private fun go() {
        walls.undo()

        runEveryTick {
            updateScore()
        }

        useTaskTimer(DURATION).whenDone {
            winManager.complete()
        }
        
        useProtector { 
            allowAll()
            
            ProtectionTypes.ALLOW_DAMAGE.disallow(this) { victim, source ->
                victim is ServerPlayer && source.entity is ServerPlayer
            }
        }
    }

    private fun updateScore() {
        val spawn = level.respawnData.pos().center

        for (player in players()) {
            val dist = player.position().subtract(spawn).horizontalDistance()

            data.setScore(player, dist)
        }
    }
}