package work.lclpnet.ap2.deadline

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
private const val ENGINE_FORCE = 2000f // throttle drive force
private const val BRAKE_FORCE = 2000f  // braking force

// resistance forces. drag is exaggerated compared to a real bike so the arena top speed stays playable
private const val DRAG = 2.56f // air resistance, grows with speed squared. balances the engine at ~60km/h
private const val ROLL = 77f   // rolling resistance, grows linearly with speed. ~30x drag

// speed clamps (m/s)
private const val MIN_SPEED = 3f  // ~11 km/h
private const val MAX_SPEED = 20f // ~72 km/h, engine max speed through drag is 60km/h

// steering
private const val TURN_RATE = 6f

// crashing
private const val CRASH_ANGLE = 60.0 // blocks hit steeper than this crash the bike
private val HEAD_ON = sin(Math.toRadians(CRASH_ANGLE))

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

    val speedFraction: Float
        get() = ((speed - MIN_SPEED) / (MAX_SPEED - MIN_SPEED)).coerceIn(0f, 1f)

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
        val engine = when {
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

        checkBlockCrash(before, dir, perTick.toDouble())
    }

    private fun checkBlockCrash(before: Vec3, dir: Vec3, dist: Double) {
        if (!sheep.horizontalCollision) return

        val moved = sheep.position().subtract(before)
        val blockedX = blocked(moved.x, dir.x * dist)
        val blockedZ = blocked(moved.z, dir.z * dist)

        if ((blockedX && blockedZ) // wedged into a corner
            || (blockedX && abs(dir.x) >= HEAD_ON)
            || (blockedZ && abs(dir.z) >= HEAD_ON)
        ) {
            crashed = true
        }
    }

    // An axis only counts as blocked when most of its intended movement was canceled.
    // This is done to avoid going up stairs / slabs crashing you.
    private fun blocked(moved: Double, intended: Double) =
        abs(intended) > 1.0e-3 && abs(moved) < abs(intended) * 0.5
}
