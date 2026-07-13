package work.lclpnet.ap2.deadline.vehicle

import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.impl.util.math.MathUtil
import kotlin.math.abs
import kotlin.math.sin

// one tick at 20 TPS in seconds
private const val TICK = 0.05f

// crashing
private const val CRASH_ANGLE = 60.0 // blocks hit steeper than this crash the bike
private val HEAD_ON = sin(Math.toRadians(CRASH_ANGLE))
private const val STEP_PROBE = 0.5 // how far ahead of the bike to probe for clearance
private const val STEP_HEIGHT = 0.65 // obstacles lower than this can be stepped onto, like stairs and slabs
private const val WALL_HEIGHT = 1.1 // obstacles at least this tall crash the bike when hit head-on

// while the bike is already climbing, it can take full block steps. this way slopes that mix
// slabs and full blocks stay climbable at an angle, while lone block walls on flat ground do not
private const val SLOPE_STEP_HEIGHT = 1.1
private const val SLOPE_WINDOW = 10 // how many ticks the slope momentum lasts after gaining height

/**
 * A mount driven like a motorbike: forces-based acceleration with A/D steering, tuned by a [BikeSpec].
 *
 * Implementation was inspired by https://asawicki.info/Mirror/Car%20Physics%20for%20Games/Car%20Physics%20for%20Games.html
 */
open class Motorbike(val mount: Mob, val spec: BikeSpec) {

    var speed = spec.minSpeed
        private set

    var crashed = false
        private set

    private var engineOverride = 0f
    private var engineOverrideTicks = 0
    private var slopeTicks = 0

    val speedFraction: Float
        get() = ((speed - spec.minSpeed) / (spec.maxSpeed - spec.minSpeed)).coerceIn(0f, 1f)

    fun overrideEngine(force: Float, durationTicks: Int) {
        engineOverride = force
        engineOverrideTicks = durationTicks
    }

    fun jump(strength: Double) {
        val velocity = mount.deltaMovement
        mount.deltaMovement = Vec3(velocity.x, strength, velocity.z)
    }

    open fun tick(input: Input) {
        steer(input)
        drive(input)
    }

    private fun steer(input: Input) {
        var turn = 0f
        if (input.left()) turn -= spec.turnRate
        if (input.right()) turn += spec.turnRate
        if (turn == 0f) return

        // constant angular rate so the turn radius (v / omega) widens naturally with speed
        val yaw = mount.yRot + turn
        mount.setYRot(yaw)
        mount.setYBodyRot(yaw)
    }

    private fun drive(input: Input) {
        if (engineOverrideTicks > 0) engineOverrideTicks--

        val engine = when {
            engineOverrideTicks > 0 -> engineOverride
            input.forward() -> spec.engineForce
            input.backward() -> -spec.brakeForce
            else -> 0f
        }

        // integrate the longitudinal forces (F = engine - drag*v^2 - roll*v) into a velocity in m/s
        val force = engine - spec.drag * speed * speed - spec.roll * speed
        speed = (speed + force / spec.mass * TICK).coerceIn(spec.minSpeed, spec.maxSpeed)

        // drive along the heading, convert m/s to blocks per tick
        val dir = MathUtil.yaw2vec(mount.yRot)
        val velocity = mount.deltaMovement
        val perTick = speed * TICK
        val before = mount.position()
        mount.deltaMovement = Vec3(dir.x * perTick, velocity.y, dir.z * perTick)
        mount.travel(Vec3.ZERO)

        if (slopeTicks > 0) slopeTicks--
        if (mount.y - before.y > 0.05) slopeTicks = SLOPE_WINDOW // gaining height keeps the slope momentum

        stepUp(before, dir, perTick.toDouble())
        checkBlockCrash(before, dir, perTick.toDouble())
    }

    // vanilla only steps up small ledges once per tick and only on the ground, which fails on ramps
    // at high speed. help the bike over steps it can clear by retrying the lost movement at step height
    private fun stepUp(before: Vec3, dir: Vec3, dist: Double) {
        if (!mount.horizontalCollision) return

        val stepHeight = if (slopeTicks > 0) SLOPE_STEP_HEIGHT else STEP_HEIGHT

        if (!canClear(dir, stepHeight)) return

        val moved = mount.position().subtract(before)
        val lostX = dir.x * dist - moved.x
        val lostZ = dir.z * dist - moved.z

        mount.move(MoverType.SELF, Vec3(lostX, stepHeight, lostZ))
        slopeTicks = SLOPE_WINDOW
    }

    private fun checkBlockCrash(before: Vec3, dir: Vec3, dist: Double) {
        if (!mount.horizontalCollision) return

        val moved = mount.position().subtract(before)
        val blockedX = blocked(moved.x, dir.x * dist)
        val blockedZ = blocked(moved.z, dir.z * dist)

        val headOn = (blockedX && blockedZ) // wedged into a corner
            || (blockedX && abs(dir.x) >= HEAD_ON)
            || (blockedZ && abs(dir.z) >= HEAD_ON)

        if (headOn && !canClear(dir, WALL_HEIGHT)) {
            crashed = true
        }
    }

    // whether the bike would fit through the space ahead when lifted by the given height
    private fun canClear(dir: Vec3, height: Double): Boolean {
        val lifted = mount.boundingBox.move(dir.x * STEP_PROBE, height, dir.z * STEP_PROBE)
        return mount.level().noCollision(mount, lifted)
    }

    // An axis only counts as blocked when most of its intended movement was canceled.
    // This is done to avoid going up stairs / slabs crashing you.
    private fun blocked(moved: Double, intended: Double) =
        abs(intended) > 1.0e-3 && abs(moved) < abs(intended) * 0.5
}
