package work.lclpnet.ap2.capture_the_flag

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball
import net.minecraft.world.level.Level
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.api.util.world.BlockPredicate
import work.lclpnet.ap2.ext.random
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
private const val SPEED = 3.0
private const val EXPLOSION_POWER = 3.5f

// a hurting projectile approaches 19 times its acceleration power as terminal velocity
private const val ACCELERATION_POWER = SPEED / 19.0

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

        fun create(level: ServerLevel, schema: CtfSchema, bases: List<Vec3>, random: Random): CtfMortar? {
            val bounds = schema.scannerBounds ?: return null
            val starts = schema.scannerStarts.toSet()

            if (starts.isEmpty()) return null

            val predicate = BlockPredicate.and({
                bounds.contains(it) && WalkableBlockPredicate.isPassable(level, it)
            }, WalkableBlockPredicate(level))

            val playArea = BfsWorldScanner(SimpleAdjacentBlocks(predicate, 1)).scan(starts)
                .asSequence()
                .toList()

            if (playArea.isEmpty()) return null

            val safeRadiusSquared = BASE_SAFE_RADIUS * BASE_SAFE_RADIUS

            val targets = playArea.filter { pos ->
                val center = Vec3.atCenterOf(pos)
                bases.none { (it.x - center.x).pow(2.0) + (it.z - center.z).pow(2.0) < safeRadiusSquared }
            }

            if (targets.isEmpty()) return null

            val launchY = playArea.maxOf { it.y } + LAUNCH_HEIGHT + 0.5

            return CtfMortar(level, targets, launchY, random)
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
