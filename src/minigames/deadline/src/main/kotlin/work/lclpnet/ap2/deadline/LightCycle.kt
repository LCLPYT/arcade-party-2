package work.lclpnet.ap2.deadline

import net.minecraft.world.entity.animal.sheep.Sheep
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec3
import work.lclpnet.ap2.impl.util.math.MathUtil

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

/**
 * A rider's dyed sheep driven like a motorbike: forces-based acceleration.
 *
 * Implementation was inspired by https://asawicki.info/Mirror/Car%20Physics%20for%20Games/Car%20Physics%20for%20Games.html
 */
class LightCycle(val sheep: Sheep) {

    var speed = MIN_SPEED
        private set

    fun tick(input: Input) {
        drive(input)
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
        sheep.deltaMovement = Vec3(dir.x * perTick, dm.y, dir.z * perTick)
        sheep.travel(Vec3.ZERO)
    }
}
