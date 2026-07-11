package work.lclpnet.ap2.task_rush.util

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.*
import net.minecraft.world.level.levelgen.Heightmap
import work.lclpnet.ap2.task_rush.task.TaskEnv
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class TRSpawns {
    private val spawned = ArrayList<Entity>()

    fun add(entity: Entity) {
        spawned.add(entity)
    }

    fun removeAll() {
        for (entity in spawned) {
            entity.discard()
        }

        spawned.clear()
    }
}

fun animals() = setOf(
    EntityTypes.CHICKEN,
    EntityTypes.COW,
    EntityTypes.PIG,
    EntityTypes.HORSE,
    EntityTypes.FOX,
    EntityTypes.GOAT,
    EntityTypes.LLAMA,
    EntityTypes.BEE,
    EntityTypes.FROG,
    EntityTypes.RABBIT,
    EntityTypes.ZOMBIE_HORSE,
)

fun monsters() = setOf(
    EntityTypes.CREEPER,
    EntityTypes.SPIDER,
    EntityTypes.PILLAGER,
    EntityTypes.SLIME,
    EntityTypes.SULFUR_CUBE,
    EntityTypes.BAT,
    EntityTypes.WITCH,
)

fun animalsAndMonsters() = buildSet {
    addAll(animals())
    addAll(monsters())
}

fun TRSpawns.spawnRandomMobs(
    env: TaskEnv,
    count: Int,
    minDistance: Double = 0.0,
    maxDistance: Double = 100.0,
    types: Set<EntityType<out Mob>> = animalsAndMonsters(),
) {
    repeat(count) {
        val type = types.random()
        val pos = randomSurfacePos(env.level, env.spawnPos, minDistance, maxDistance)
        val entity = type.create(env.level, EntitySpawnReason.COMMAND) ?: return@repeat

        entity.setPos(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
        entity.setPersistenceRequired()

        env.level.addFreshEntity(entity)
        add(entity)
    }
}

fun randomSurfacePos(level: ServerLevel, spawn: BlockPos, minDistance: Double, maxDistance: Double): BlockPos {
    val minDistance = minDistance.coerceAtMost(maxDistance)
    val maxDistance = maxDistance.coerceAtLeast(minDistance)

    val angle = level.random.nextDouble() * 2.0 * PI
    val distance = minDistance + level.random.nextDouble() * (maxDistance - minDistance)

    val x = spawn.x + (cos(angle) * distance).toInt()
    val z = spawn.z + (sin(angle) * distance).toInt()
    val y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)

    return BlockPos(x, y, z)
}
