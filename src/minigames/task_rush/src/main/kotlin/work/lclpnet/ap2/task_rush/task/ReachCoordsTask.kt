package work.lclpnet.ap2.task_rush.task

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.DyeColor
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.seconds

/**
 * Be the first to reach a set of coordinates near spawn (~200 blocks away).
 * Players are shown the target and their live distance to it.
 */
object ReachCoordsTask : OrderTask("reach_coords", 90.seconds) {

    private const val DISTANCE = 200.0
    private const val REACH_RADIUS = 3.0

    private var platform: AreaPlatform? = null

    override fun begin(env: TaskEnv) {
        val target = pickTarget(env.level, env.spawnPos)
        val center = Vec3.atCenterOf(target)

        platform = AreaPlatform.create(env.level, target, DyeColor.LIME, env.chunkPersistence)

        lateinit var progress: Progress

        progress = start(env) {
            val remaining = env.players.sortedBy {
                val dx = it.x - center.x
                val dz = it.z - center.z
                dx * dx + dz * dz
            }

            progress.rankRemaining(remaining)
        }

        env.translations.translateText("task.reach_coords.target", Component.literal("${target.x} ${target.y} ${target.z}"))
            .sendTo(env.players)

        env.scheduler.interval(1) { ->
            for (player in env.players) {
                val dx = player.x - center.x
                val dz = player.z - center.z

                val distance = sqrt(dx * dx + dz * dz)
                env.translations.translateText("task.reach_coords.distance", Component.literal(distance.toInt().toString()))
                    .sendTo(player, true)

                if (dx * dx + dz * dz <= REACH_RADIUS * REACH_RADIUS) {
                    progress.finish(player)
                }
            }
        }
    }

    override fun end(env: TaskEnv) {
        platform?.remove()
        platform = null
    }

    private fun pickTarget(level: ServerLevel, spawn: BlockPos): BlockPos {
        val angle = level.random.nextDouble() * 2.0 * Math.PI
        val x = spawn.x + (cos(angle) * DISTANCE).toInt()
        val z = spawn.z + (sin(angle) * DISTANCE).toInt()
        val y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)

        return BlockPos(x, y, z)
    }
}
