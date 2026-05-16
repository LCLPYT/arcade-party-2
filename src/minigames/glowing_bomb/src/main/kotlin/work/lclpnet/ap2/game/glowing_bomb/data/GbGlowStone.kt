package work.lclpnet.ap2.game.glowing_bomb.data

import net.minecraft.world.level.block.Blocks
import org.joml.Quaterniond
import org.joml.Vector3d
import work.lclpnet.gaco.math.solver.NumericalSolver
import work.lclpnet.gaco.math.solver.RungeKuttaSolver
import work.lclpnet.gaco.math.solver.SimpleGravityGradient
import work.lclpnet.gaco.math.solver.StateVector
import work.lclpnet.gaco.scene.Object3d
import work.lclpnet.gaco.scene.Scene
import work.lclpnet.gaco.scene.animation.Animatable
import work.lclpnet.gaco.scene.animation.Animation
import work.lclpnet.gaco.scene.animation.AnimationContext
import work.lclpnet.gaco.scene.`object`.BlockDisplayObject
import kotlin.math.PI
import kotlin.math.sqrt

private const val TWO_PI = PI * 2
private val ROTATION_AXIS = 1.0 / sqrt(3.0)
private const val GRAVITY_ACCELERATION = 9.81

class GbGlowStone(scene: Scene, initialAngle: Double, incline: Double, orbitSpeed: Double, rotationSpeed: Double) : Object3d(scene), Animatable {

    private val orbitAnimation = Animation(
        OrbitAnimation(initialAngle, incline, orbitSpeed, rotationSpeed)
    ).running()
    private var yieldAnimation: Animation? = null

    init {
        val glowStone = BlockDisplayObject(scene, Blocks.GLOWSTONE.defaultBlockState())
        glowStone.position.set(-0.5, -0.5, -0.5)  // center to origin
        addChild(glowStone)
    }

    override fun updateAnimation(dt: Double, ctx: AnimationContext) {
        orbitAnimation.updateAnimation(dt, ctx)
        yieldAnimation?.updateAnimation(dt, ctx)
    }

    fun yieldInto(anchor: GbAnchor, manager: GbManager) {
        orbitAnimation.stop()
        yieldAnimation = Animation(YieldAnimation(anchor, manager)).also { it.start() }
    }

    private inner class OrbitAnimation(
        initialAngle: Double,
        incline: Double,
        private val orbitAngularVelocity: Double,
        private val rotationAngularVelocity: Double
    ) : Animatable {

        private val orbitRotation = Quaterniond().also { it.setAngleAxis(incline, 1.0, 0.0, 0.0) }
        private var orbitAngle = initialAngle
        private var rotationAngle = initialAngle

        override fun updateAnimation(dt: Double, ctx: AnimationContext) {
            orbitAngle = (orbitAngle + orbitAngularVelocity * dt) % TWO_PI
            rotationAngle = (rotationAngle + rotationAngularVelocity * dt) % TWO_PI

            if (orbitAngle >= PI) orbitAngle -= TWO_PI
            if (rotationAngle >= PI) rotationAngle -= TWO_PI

            val x = 0.3 * Math.cos(orbitAngle)
            val z = 0.3 * Math.sin(orbitAngle)

            position.set(x, 0.0, z)
            orbitRotation.transform(position)

            rotation.setAngleAxis(rotationAngle, ROTATION_AXIS, ROTATION_AXIS, ROTATION_AXIS)
        }
    }

    private inner class YieldAnimation(private val anchor: GbAnchor, private val manager: GbManager) : Animatable {

        private val targetPos: Vector3d
        private val state: StateVector
        private val solver: NumericalSolver = RungeKuttaSolver.INSTANCE
        private val gradient = SimpleGravityGradient(GRAVITY_ACCELERATION)
        private var complete = false

        init {
            val anchorPos = anchor.pos
            targetPos = Vector3d(anchorPos.x() + 0.5, anchorPos.y() + 0.5, anchorPos.z() + 0.5)

            val worldPos = worldTranslation()
            val velocity = gradient.getLaunchVelocity(worldPos, targetPos, 2.0)
            state = StateVector(arrayOf(worldPos, velocity))
        }

        override fun updateAnimation(dt: Double, ctx: AnimationContext) {
            val distanceSq = worldTranslation().distanceSquared(targetPos)

            if (distanceSq <= 0.25) {
                if (!complete) {
                    complete = true
                    detach()
                    manager.addCharge(anchor)
                }
                return
            }

            solver.solve(state, dt, gradient)
            setWorldPosition(state.getVector3(0))
        }
    }
}
