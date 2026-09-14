package work.lclpnet.ap2.capture_the_flag

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.api.util.world.BlockPredicate
import work.lclpnet.ap2.ext.random
import work.lclpnet.ap2.impl.util.debug.DebugController
import work.lclpnet.ap2.impl.util.world.BfsWorldScanner
import work.lclpnet.ap2.impl.util.world.SimpleAdjacentBlocks
import work.lclpnet.ap2.impl.util.world.WalkableBlockPredicate
import java.util.*
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val LAUNCH_DELAY = 3.seconds..7.seconds
private const val LAUNCH_HEIGHT = 50
private const val BASE_SAFE_RADIUS = 15.0
private const val PLAY_AREA_BORDER_SAFE_DISTANCE = 9
private const val SPEED = 3.0
private const val EXPLOSION_POWER = 3.5f
private const val DEBUG_SCANNER = false
private const val DEBUG_FINAL_POSITIONS = false

// a hurting projectile approaches 19 times its acceleration power as terminal velocity
private const val ACCELERATION_POWER = SPEED / 19.0

private val CARDINAL_DIRECTIONS = arrayOf(
    -1 to 0,
    1 to 0,
    0 to -1,
    0 to 1,
)

/**
 * Drops fireballs onto random spots of the play area to simulate mortar fire.
 */
class CtfMortar(
    private val level: ServerLevel,
    private val targets: List<BlockPos>,
    private val launchY: Double,
    private val random: Random,
) {

    fun nextDelay(): Duration = LAUNCH_DELAY.random()

    fun fire() {
        val target = targets[random.nextInt(targets.size)]

        val fireball = MortarFireball(level)
        fireball.setPos(target.x + 0.5, launchY, target.z + 0.5)
        fireball.deltaMovement = Vec3(0.0, -SPEED, 0.0)
        fireball.accelerationPower = ACCELERATION_POWER

        level.addFreshEntity(fireball)
    }

    companion object {

        const val TUBES = 3

        fun create(
            level: ServerLevel,
            schema: CtfSchema,
            bases: List<Vec3>,
            random: Random,
            debugController: DebugController,
        ): CtfMortar? {
            val bounds = schema.scannerBounds ?: return null
            val starts = schema.scannerStarts.toSet()

            if (starts.isEmpty()) return null

            val predicate = BlockPredicate {
                bounds.contains(it) && WalkableBlockPredicate.isPassable(level, it)
            }

            val playArea = BfsWorldScanner(SimpleAdjacentBlocks(predicate, 0)).scan(starts)
                .asSequence()
                .map { level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, it) }
                .toList()

            if (playArea.isEmpty()) return null

            if (DEBUG_SCANNER) {
                debugController.visualizeBlockPositions(playArea, Blocks.STAINED_GLASS.blue.defaultBlockState())
            }

            val borderInsetPlayArea = maskBorder(playArea)

            val targets = maskBaseLocations(borderInsetPlayArea, bases)

            if (targets.isEmpty()) return null

            if (DEBUG_FINAL_POSITIONS) {
                debugController.visualizeBlockPositions(targets, Blocks.STAINED_GLASS.green.defaultBlockState())
            }

            val launchY = playArea.maxOf { it.y } + LAUNCH_HEIGHT + 0.5

            return CtfMortar(level, targets, launchY, random)
        }

        private fun maskBorder(playArea: List<BlockPos>): List<BlockPos> {
            val minX = playArea.minOf { it.x }
            val minZ = playArea.minOf { it.z }

            // pad by one cell on each side, so that the outside of the bounding box seeds the distances
            val width = playArea.maxOf { it.x } - minX + 3
            val length = playArea.maxOf { it.z } - minZ + 3

            fun index(pos: BlockPos) = (pos.x - minX + 1) * length + (pos.z - minZ + 1)

            val distances = IntArray(width * length)

            for (pos in playArea) {
                distances[index(pos)] = Int.MAX_VALUE
            }

            val queue = ArrayDeque<Int>()

            for (i in distances.indices) {
                if (distances[i] == 0) queue.add(i)
            }

            while (queue.isNotEmpty()) {
                val i = queue.removeFirst()
                val x = i / length
                val z = i % length
                val dist = distances[i] + 1

                for ((dx, dz) in CARDINAL_DIRECTIONS) {
                    val tx = x + dx
                    val tz = z + dz

                    if (tx !in 0..<width || tz !in 0..<length) continue

                    val j = tx * length + tz

                    if (distances[j] <= dist) continue

                    distances[j] = dist
                    queue.add(j)
                }
            }

            return playArea.filter { distances[index(it)] > PLAY_AREA_BORDER_SAFE_DISTANCE }
        }

        private fun maskBaseLocations(
            playArea: List<BlockPos>,
            bases: List<Vec3>,
        ): List<BlockPos> {
            val safeRadiusSquared = BASE_SAFE_RADIUS * BASE_SAFE_RADIUS

            val targets = playArea.filter { pos ->
                val center = Vec3.atCenterOf(pos)
                bases.none { (it.x - center.x).pow(2.0) + (it.z - center.z).pow(2.0) < safeRadiusSquared }
            }
            return targets
        }
    }
}

private class MortarFireball(level: Level) : Fireball(EntityTypes.FIREBALL, level) {

    override fun onHit(hitResult: HitResult) {
        super.onHit(hitResult)

        val world = level()

        if (world !is ServerLevel) return

        world.explode(this, null, null, x, y, z, EXPLOSION_POWER, true, Level.ExplosionInteraction.TNT)

        discard()
    }
}
