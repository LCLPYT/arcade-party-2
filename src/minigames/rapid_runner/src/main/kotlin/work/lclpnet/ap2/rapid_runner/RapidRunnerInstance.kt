package work.lclpnet.ap2.rapid_runner

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.network.chat.numbers.StyledFormat
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.scores.DisplaySlot
import work.lclpnet.ap2.api.stats.CommonStats
import work.lclpnet.ap2.ext.allPlayers
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.ext.players
import work.lclpnet.ap2.ext.runEveryTick
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.data.DoubleScoreDataContainer
import work.lclpnet.ap2.game.util.*
import work.lclpnet.ap2.util.scoreboard.setupTranslatedSidebarObjective
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

    val data = useDataContainer { DoubleScoreDataContainer(it) }
    val winManager = useFFAWinManager(null) { data }
    val stats = useFFAStats(winManager, data, CommonStats.DoubleScore, listOf())
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

    private fun setupObjective() {
        val objective = setupTranslatedSidebarObjective(gameHandle.scoreboardManager, "game.ap2.rapid_runner.distance")

//        useScoreboardStatsSync(data, objective)
        objective.setSlot(DisplaySlot.SIDEBAR)
        objective.setNumberFormat(StyledFormat.PLAYER_LIST_DEFAULT)

        for (player in PlayerLookup.all(gameHandle.server)) {
            objective.add(player)
        }
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
                (victim is ServerPlayer && source.entity is ServerPlayer) || source.isOf(DamageTypes.FALL)
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