package work.lclpnet.ap2.task_rush.task

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.DyeColor
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
 * Stand the longest inside a marked 3x3x3 area near spawn. Time only counts while a player is alone in
 * the area.
 */
object StandInAreaTask : Task {

    override val id = "stand_in_area"
    private const val DISTANCE = 30.0

    private var platform: AreaPlatform? = null

    override fun begin(env: TaskEnv) {
        val base = pickArea(env.level, env.spawnPos)
        val box = AABB(
            (base.x - 1).toDouble(), base.y.toDouble(), (base.z - 1).toDouble(),
            (base.x + 2).toDouble(), (base.y + 3).toDouble(), (base.z + 2).toDouble()
        )

        env.pvpDisabled = false

        platform = AreaPlatform.create(env.level, base, DyeColor.LIME, env.chunkPersistence)

        env.translations.translateText("task.stand_in_area.location", Component.literal("${base.x} ${base.y} ${base.z}"))
            .sendTo(env.players)

        val ticksInside = HashMap<UUID, Int>()

        env.scheduler.interval(1) { ->
            val inside = env.players.filter { box.intersects(it.boundingBox) }

            if (inside.size != 1) return@interval

            val player = inside[0]
            val ticks = (ticksInside[player.uuid] ?: 0) + 1
            ticksInside[player.uuid] = ticks

            if (ticks % 20 == 0) {
                env.feedback(player, "task.feedback.stand_in_area", ticks / 20, sound = true)
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

    override fun end(env: TaskEnv) {
        platform?.remove()
        platform = null
    }

    private fun pickArea(level: ServerLevel, spawn: BlockPos): BlockPos {
        val angle = level.random.nextDouble() * 2.0 * Math.PI
        val x = spawn.x + (cos(angle) * DISTANCE).toInt()
        val z = spawn.z + (sin(angle) * DISTANCE).toInt()
        val y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)

        return BlockPos(x, y, z)
    }
}
