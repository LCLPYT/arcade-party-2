package work.lclpnet.ap2.game.paintball.util

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.Vec3
import org.json.JSONObject
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.data.type.TeamRef
import work.lclpnet.ap2.game.team.Team
import work.lclpnet.ap2.game.util.Announcer
import work.lclpnet.ap2.game.util.WinManager
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.title.AnimatedTitle
import work.lclpnet.kibu.scheduler.Ticks

private val RESULT_DELAY_TICKS = Ticks.seconds(3)

data class ResultSpot(val pos: Vec3, val yaw: Float, val pitch: Float)

fun resultSpotFromJson(json: JSONObject): ResultSpot {
    val pos = MapUtil.readCenteredVec3d(json.getJSONArray("pos"))
    val yaw = MapUtil.readAngle(json.optNumber("yaw", 0))
    val pitch = MapUtil.readAngle(json.optNumber("pitch", 0))

    return ResultSpot(pos, yaw, pitch)
}

class PaintballResults(
    private val gameHandle: MiniGameHandle,
    private val announcer: Announcer,
    private val world: ServerLevel,
    private val resultSpot: ResultSpot,
    private val data: IntScoreDataContainer<Team, TeamRef>,
    private val winManager: WinManager<Team, TeamRef>,
    private val teamRefs: () -> List<TeamRef>
) {
    fun beginResults() {
        gameHandle.resetGameScheduler()

        teleportPlayersToResults()

        gameHandle.scheduler.interval(1, ::teleportPlayersToResults)

        announcer.announce("game_over", null)

        gameHandle.scheduler.timeout(RESULT_DELAY_TICKS) { ->
            showResults()
        }
    }

    private fun teleportPlayersToResults() {
        val pos = resultSpot.pos

        for (player in PlayerLookup.all(gameHandle.server)) {
            player.setGameMode(GameType.SPECTATOR)
            player.abilities.flyingSpeed = 0f
            player.onUpdateAbilities()
            player.teleportTo(world, pos.x(), pos.y(), pos.z(), emptySet(), resultSpot.yaw, resultSpot.pitch, true)
        }
    }

    private fun showResults() {
        val animatedTitle = AnimatedTitle()

        animatedTitle.add(PaintballResultAnimation(teamRefs(), data, gameHandle.server, gameHandle.translations) {
            for (player in PlayerLookup.all(gameHandle.server)) {
                player.setGameMode(GameType.SPECTATOR)
                player.abilities.flyingSpeed = 0.05f
                player.onUpdateAbilities()
            }

            winManager.complete()
        })

        animatedTitle.start(gameHandle.scheduler, 1)

        gameHandle.whenDone(animatedTitle::stop)
    }
}
