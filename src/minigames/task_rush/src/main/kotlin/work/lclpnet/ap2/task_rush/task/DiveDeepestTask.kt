package work.lclpnet.ap2.task_rush.task

import work.lclpnet.ap2.game.data.DoubleScoreDataContainer
import work.lclpnet.ap2.game.data.Ordering
import work.lclpnet.ap2.game.data.type.PlayerRef
import java.util.*
import kotlin.time.Duration.Companion.seconds

/**
 * Dive the deepest into water.
 * The score is the height difference between entering the water and the player's position at the end of the task.
 */
object DiveDeepestTask : Task {

    override val id = "dive_deepest"

    override fun begin(env: TaskEnv) {
        val entryY = HashMap<UUID, Double>()

        env.scheduler.interval(1) { ->
            for (player in env.players) {
                if (player.isInWater || player.isUnderWater) {
                    player.airSupply = player.maxAirSupply

                    if (player.uuid !in entryY) {
                        entryY[player.uuid] = player.y
                    }
                } else {
                    entryY.remove(player.uuid)
                }
            }
        }

        env.timer("task.$id.task", 30.seconds) {
            val data = DoubleScoreDataContainer(PlayerRef::create, Ordering.DESCENDING, "score.dive_depth")

            for (player in env.players) {
                val entry = entryY[player.uuid]
                val depth = if (entry != null) (entry - player.y).coerceAtLeast(0.0) else 0.0

                data.setScore(player, depth)
            }

            env.complete(data)
        }
    }
}
