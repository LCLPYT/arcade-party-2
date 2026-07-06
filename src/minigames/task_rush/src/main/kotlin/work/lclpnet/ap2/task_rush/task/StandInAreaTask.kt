package work.lclpnet.ap2.task_rush.task

import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.AABB
import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.data.Ordering
import work.lclpnet.ap2.game.data.type.PlayerRef
import java.util.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration.Companion.seconds

/**
 * Stand the longest inside a marked 2x2x2 area near spawn. Time only counts while a player is alone in
 * the area.
 */
object StandInAreaTask : Task {

    override val id = "stand_in_area"
    private const val DISTANCE = 30.0

    override fun begin(env: TaskEnv) {
        val min = pickArea(env.level, env.spawnPos)
        val box = AABB(
            min.x.toDouble(), min.y.toDouble(), min.z.toDouble(),
            (min.x + 2).toDouble(), (min.y + 2).toDouble(), (min.z + 2).toDouble()
        )

        env.translations.translateText("task.stand_in_area.location", Component.literal("${min.x} ${min.y} ${min.z}"))
            .sendTo(env.players)

        val ticksInside = HashMap<UUID, Int>()

        env.scheduler.interval(1) { ->
            drawMarker(env.level, min)

            val inside = env.players.filter { box.intersects(it.boundingBox) }
            if (inside.size == 1) {
                val player = inside[0]
                ticksInside[player.uuid] = (ticksInside[player.uuid] ?: 0) + 1
            }
        }

        env.timer("task.$id.task", 45.seconds) {
            val data = IntScoreDataContainer(PlayerRef::create, Ordering.DESCENDING, "ap2.time.seconds")

            for (player in env.players) {
                val seconds = (ticksInside[player.uuid] ?: 0) / 20
                data.setScore(player, seconds)
            }

            env.complete(data)
        }
    }

    private fun pickArea(level: ServerLevel, spawn: BlockPos): BlockPos {
        val angle = level.random.nextDouble() * 2.0 * Math.PI
        val x = spawn.x + (cos(angle) * DISTANCE).toInt()
        val z = spawn.z + (sin(angle) * DISTANCE).toInt()
        val y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)

        return BlockPos(x, y, z)
    }

    private fun drawMarker(level: ServerLevel, min: BlockPos) {
        val x = min.x + 1.0
        val y = min.y + 1.0
        val z = min.z + 1.0

        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 8, 1.0, 1.0, 1.0, 0.0)
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, min.y + 2.5, z, 4, 0.6, 0.2, 0.6, 0.0)
    }
}
