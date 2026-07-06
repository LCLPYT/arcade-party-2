package work.lclpnet.ap2.task_rush.task

import net.minecraft.world.entity.vehicle.boat.AbstractBoat
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.game.data.DoubleScoreDataContainer
import work.lclpnet.ap2.game.data.Ordering
import work.lclpnet.ap2.game.data.type.PlayerRef
import java.util.*
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.seconds

/**
 * Move the most distance in water. Swimming and travelling in a boat both count.
 */
object WaterDistanceTask : Task {

    override val id = "water_distance"

    override fun begin(env: TaskEnv) {
        val data = DoubleScoreDataContainer(PlayerRef::create, Ordering.DESCENDING, "score.water_distance")
        val lastPos = HashMap<UUID, Vec3>()

        for (player in env.players) {
            data.setScore(player, 0.0)
        }

        env.scheduler.interval(1) { ->
            for (player in env.players) {
                val inWater = player.isInWater || player.isUnderWater || player.vehicle is AbstractBoat
                val prev = lastPos.put(player.uuid, player.position())

                if (inWater && prev != null) {
                    val dx = player.x - prev.x
                    val dz = player.z - prev.z
                    val dist = sqrt(dx * dx + dz * dz)

                    if (dist > 0) {
                        data.addScore(player, dist)
                        env.feedback(player, "task.feedback.water_distance", data.getScore(player).toInt())
                    }
                }
            }
        }

        env.timer("task.$id.task", 30.seconds) {
            env.complete(data)
        }
    }
}
