package work.lclpnet.ap2.task_rush.task

import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.data.Ordering
import work.lclpnet.ap2.game.data.type.PlayerRef
import java.util.*
import kotlin.time.Duration.Companion.seconds

/**
 * Dive the deepest into water.
 * The score is the greatest depth reached below the point where the player entered the water.
 */
object DiveDeepestTask : Task {

    override val id = "dive_deepest"

    override fun begin(env: TaskEnv) {
        val entryY = HashMap<UUID, Double>()
        val bestDepth = HashMap<UUID, Int>()

        env.scheduler.interval(1) { ->
            for (player in env.players) {
                if (player.isInWater || player.isUnderWater) {
                    player.airSupply = player.maxAirSupply

                    val entry = entryY.getOrPut(player.uuid) { player.y }
                    val depth = (entry - player.y).coerceAtLeast(0.0).toInt()

                    if (depth > (bestDepth[player.uuid] ?: 0)) {
                        bestDepth[player.uuid] = depth
                        env.feedback(player, "task.feedback.dive_depth", depth)
                    }
                } else {
                    entryY.remove(player.uuid)
                }
            }
        }

        env.timer("task.$id.task", 30.seconds) {
            val data = IntScoreDataContainer(PlayerRef::create, Ordering.DESCENDING, "score.dive_depth")

            for (player in env.players) {
                data.setScore(player, bestDepth[player.uuid] ?: 0)
            }

            env.complete(data)
        }
    }
}
