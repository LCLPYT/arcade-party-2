package work.lclpnet.ap2.task_rush.task

import work.lclpnet.ap2.game.data.DoubleScoreDataContainer
import work.lclpnet.ap2.game.data.Ordering
import work.lclpnet.ap2.game.data.type.PlayerRef
import java.util.*
import kotlin.time.Duration.Companion.seconds

/**
 * Fall the longest distance into a water block.
 * The distance fallen just before hitting water counts.
 */
object FallIntoWaterTask : Task {

    override val id = "fall_into_water"

    override fun begin(env: TaskEnv) {
        val data = DoubleScoreDataContainer(PlayerRef::create, Ordering.DESCENDING, "score.fall_distance")
        val wasInWater = HashMap<UUID, Boolean>()
        val prevFall = HashMap<UUID, Double>()

        for (player in env.players) {
            data.setScore(player, 0.0)
        }

        env.scheduler.interval(1) { ->
            for (player in env.players) {
                val inWater = player.isInWater || player.isUnderWater
                val was = wasInWater.put(player.uuid, inWater) ?: false

                if (inWater && !was) {
                    // just entered the water: the fall distance accumulated before the water reset it
                    val fall = prevFall[player.uuid] ?: player.fallDistance

                    if (fall > data.getScore(player)) {
                        data.setScore(player, fall)
                        env.feedback(player, "task.feedback.fall_into_water", fall.toInt(), sound = true)
                    }
                }

                prevFall[player.uuid] = player.fallDistance
            }
        }

        env.timer("task.$id.task", 30.seconds) {
            env.complete(data)
        }
    }
}
