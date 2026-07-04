package work.lclpnet.ap2.deadline

import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.animal.sheep.Sheep
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.impl.util.math.MathUtil
import kotlin.math.abs
import kotlin.math.sin

// one tick at 20 TPS in seconds
private const val TICK = 0.05f

// bike + rider mass (kg)
private const val MASS = 250f

// longitudinal forces (newtons)
private const val ENGINE_FORCE = 3000f // throttle drive force
private const val BRAKE_FORCE = 3000f  // braking force

// resistance forces. drag is exaggerated compared to a real bike so the arena top speed stays playable
private const val DRAG = 1.42f // air resistance, grows with speed squared. balances the engine at ~120km/h
private const val ROLL = 42.6f // rolling resistance, grows linearly with speed. ~30x drag

// speed clamps (m/s)
private const val MIN_SPEED = 6f  // ~22 km/h
private const val MAX_SPEED = 40f // ~144 km/h, engine max speed through drag is 120km/h

// steering
private const val TURN_RATE = 6f

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
 * A rider's dyed sheep driven like a motorbike: forces-based acceleration with A/D steering.
 *
 * Implementation was inspired by https://asawicki.info/Mirror/Car%20Physics%20for%20Games/Car%20Physics%20for%20Games.html
 */
class LightCycle(val sheep: Sheep) {

    var speed = MIN_SPEED
        private set

    var crashed = false
        private set

    /** Whether the bike currently passes through trails. */
    val phased: Boolean
        get() = phaseTicks > 0

    private var engineOverride = 0f
    private var engineOverrideTicks = 0
    private var phaseTicks = 0
    private var slopeTicks = 0

    val speedFraction: Float
        get() = ((speed - MIN_SPEED) / (MAX_SPEED - MIN_SPEED)).coerceIn(0f, 1f)

    fun overrideEngine(force: Float, durationTicks: Int) {
        engineOverride = force
        engineOverrideTicks = durationTicks
    }

    fun jump(strength: Double) {
        val dm = sheep.deltaMovement
        sheep.deltaMovement = Vec3(dm.x, strength, dm.z)
    }

    fun phase(durationTicks: Int) {
        phaseTicks = durationTicks
    }

    fun tick(input: Input) {
        steer(input)
        drive(input)
    }

    private fun steer(input: Input) {
        var turn = 0f
        if (input.left()) turn -= TURN_RATE
        if (input.right()) turn += TURN_RATE
        if (turn == 0f) return

        // constant angular rate so the turn radius (v / omega) widens naturally with speed
        val yaw = sheep.yRot + turn
        sheep.setYRot(yaw)
        sheep.setYBodyRot(yaw)
    }

    private fun drive(input: Input) {
        if (engineOverrideTicks > 0) engineOverrideTicks--
        if (phaseTicks > 0) phaseTicks--

        val engine = when {
            engineOverrideTicks > 0 -> engineOverride // an overridden engine forces full throttle
            input.forward() -> ENGINE_FORCE // throttle
            input.backward() -> -BRAKE_FORCE // brake
            else -> 0f // coasting, resistance only
        }

        // integrate the longitudinal forces (F = engine - drag*v^2 - roll*v) into a velocity in m/s
        val force = engine - DRAG * speed * speed - ROLL * speed
        speed = (speed + force / MASS * TICK).coerceIn(MIN_SPEED, MAX_SPEED)

        // drive along the heading, convert m/s to blocks per tick
        val dir = MathUtil.yaw2vec(sheep.yRot)
        val dm = sheep.deltaMovement
        val perTick = speed * TICK
        val before = sheep.position()
        sheep.deltaMovement = Vec3(dir.x * perTick, dm.y, dir.z * perTick)
        sheep.travel(Vec3.ZERO)

        if (slopeTicks > 0) slopeTicks--
        if (sheep.y - before.y > 0.05) slopeTicks = SLOPE_WINDOW // gaining height keeps the slope momentum

        stepUp(before, dir, perTick.toDouble())
        checkBlockCrash(before, dir, perTick.toDouble())
    }

    // vanilla only steps up small ledges once per tick and only on the ground, which fails on ramps
    // at high speed. help the bike over steps it can clear by retrying the lost movement at step height
    private fun stepUp(before: Vec3, dir: Vec3, dist: Double) {
        if (!sheep.horizontalCollision) return

        val stepHeight = if (slopeTicks > 0) SLOPE_STEP_HEIGHT else STEP_HEIGHT

        if (!canClear(dir, stepHeight)) return

        val moved = sheep.position().subtract(before)
        val lostX = dir.x * dist - moved.x
        val lostZ = dir.z * dist - moved.z

        sheep.move(MoverType.SELF, Vec3(lostX, stepHeight, lostZ))
        slopeTicks = SLOPE_WINDOW
    }

    private fun checkBlockCrash(before: Vec3, dir: Vec3, dist: Double) {
        if (!sheep.horizontalCollision) return

        val moved = sheep.position().subtract(before)
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
        val lifted = sheep.boundingBox.move(dir.x * STEP_PROBE, height, dir.z * STEP_PROBE)
        return sheep.level().noCollision(sheep, lifted)
    }

    // An axis only counts as blocked when most of its intended movement was canceled.
    // This is done to avoid going up stairs / slabs crashing you.
    private fun blocked(moved: Double, intended: Double) =
        abs(intended) > 1.0e-3 && abs(moved) < abs(intended) * 0.5
}
