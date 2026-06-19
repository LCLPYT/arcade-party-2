package work.lclpnet.ap2.game.glowing_bomb.data

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import org.joml.Vector3d
import work.lclpnet.gaco.scene.Object3d
import work.lclpnet.gaco.scene.Scene
import work.lclpnet.gaco.scene.animation.Animatable
import work.lclpnet.gaco.scene.animation.Animation
import work.lclpnet.gaco.scene.animation.AnimationContext
import work.lclpnet.gaco.scene.`object`.BlockDisplayObject
import work.lclpnet.gaco.scene.`object`.ItemDisplayObject
import java.util.*

private const val PARTICLE_PERIOD_SECONDS = 0.2
private const val BEEP_DELAY_SECONDS = 1.3
private const val LAMP_DURATION_SECONDS = 0.45
private const val ROTATION_SPEED = Math.PI / 6
private const val HOVER_SPEED = 0.15
private const val HOVER_AMPLITUDE = 0.15
private const val YIELD_DELAY_SECONDS = 0.75

class GbBomb(scene: Scene, private val onYielded: Runnable) : Object3d(scene), Animatable {

    private val lampActiveStack = ItemStack(Items.REDSTONE_TORCH)
    private val lampInactiveStack = ItemStack(Items.LEVER)
    private val glowStones = mutableListOf<GbGlowStone>()
    private val lever: ItemDisplayObject
    private val idleAnimation = Animation(IdleAnimation()).running()
    private var yieldAnimation: Animation? = null
    private var requiredYieldCount = 0
    private var yielded = 0

    init {
        val v = 0.0625
        val w = 1.125

        // lower
        frame(0.0, -v, -v, 1.0, v, v)
        frame(0.0, -v, 1.0, 1.0, v, v)
        frame(-v, -v, -v, v, v, w)
        frame(1.0, -v, -v, v, v, w)

        // upper
        frame(0.0, 1.0, -v, 1.0, v, v)
        frame(0.0, 1.0, 1.0, 1.0, v, v)
        frame(-v, 1.0, -v, v, v, w)
        frame(1.0, 1.0, -v, v, v, w)

        // sides
        frame(1.0, 0.0, -v, v, 1.0, v)
        frame(1.0, 0.0, 1.0, v, 1.0, v)
        frame(-v, 0.0, 1.0, v, 1.0, v)
        frame(-v, 0.0, -v, v, 1.0, v)

        val glass = BlockDisplayObject(scene, Blocks.TINTED_GLASS.defaultBlockState())
        glass.position.set(-0.5, -0.5, -0.5)  // glass center to origin
        addChild(glass)

        lever = ItemDisplayObject(scene, lampInactiveStack)
        lever.position.set(0.875 - 0.5, 1.1875 - 0.5, 0.1875 - 0.5)
        lever.rotation.setAngleAxis(0.5235987755982988, 0.0, 1.0, 0.0)
        lever.scale.set(0.5)
        addChild(lever)
    }

    private fun frame(px: Double, py: Double, pz: Double, sx: Double, sy: Double, sz: Double) {
        val frame = BlockDisplayObject(scene, Blocks.CONCRETE.red.defaultBlockState())
        frame.position.set(-0.5, -0.5, -0.5)  // cube center to origin
        frame.scale.set(sx, sy, sz)

        val pivot = Object3d(scene)
        pivot.position.set(px, py, pz)
        pivot.addChild(frame)

        addChild(pivot)
    }

    fun setGlowStoneAmount(amount: Int, random: Random) {
        glowStones.forEach { removeChild(it) }
        glowStones.clear()

        val incline = Math.PI / amount

        for (i in 0 until amount) {
            val initialAngle = random.nextDouble() * Math.PI * 2 - Math.PI
            val orbitSpeed = Math.PI * (random.nextDouble() * 0.4 + 0.55)
            val rotationSpeed = Math.PI * (random.nextDouble() * 0.3 + 1.1)

            val glowStone = GbGlowStone(scene, initialAngle, i * incline, orbitSpeed, rotationSpeed)
            glowStone.scale.set(0.2)

            glowStones.add(glowStone)
            addChild(glowStone)
        }
    }

    val glowStoneAmount: Int get() = glowStones.size

    override fun updateAnimation(dt: Double, ctx: AnimationContext) {
        idleAnimation.updateAnimation(dt, ctx)
        yieldAnimation?.updateAnimation(dt, ctx)
    }

    override fun onChildRemoved(child: Object3d) {
        if (child !is GbGlowStone) return

        yielded++

        if (yielded == requiredYieldCount) {
            onYielded.run()
        }
    }

    fun yieldGlowStone(manager: GbManager, anchor: GbAnchor) {
        idleAnimation.stop()
        yieldAnimation = Animation(YieldAnimation(manager, anchor)).also { it.start() }
    }

    private inner class IdleAnimation : Animatable {

        private var particleTime = 0.0
        private var beepTime = 0.0
        private var lampTime = 0.0
        private var rotationY = 0.0
        private var hoverDirection = 1
        private var elevation = 0.0

        override fun updateAnimation(dt: Double, ctx: AnimationContext) {
            particleTime += dt
            beepTime += dt

            if (particleTime >= PARTICLE_PERIOD_SECONDS) {
                particleTime -= PARTICLE_PERIOD_SECONDS

                val fusePos = Vector3d(0.0, 0.65, 0.0)
                matrixWorld.transformPosition(fusePos)

                ctx.world().sendParticles(ParticleTypes.SMOKE, fusePos.x(), fusePos.y(), fusePos.z(), 0, 0.0, 1.0, 0.0, 0.05)
            }

            if (lever.stack == lampActiveStack) {
                lampTime += dt

                if (lampTime >= LAMP_DURATION_SECONDS) {
                    lampTime -= LAMP_DURATION_SECONDS
                    lever.setStack(lampInactiveStack)
                }
            }

            if (beepTime >= BEEP_DELAY_SECONDS) {
                beepTime -= BEEP_DELAY_SECONDS

                lever.setStack(lampActiveStack)

                val pos = Vector3d(0.0, 0.0, 0.0)
                matrixWorld.transformPosition(pos)

                ctx.world().playSound(null, pos.x(), pos.y(), pos.z(), SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 0.75f, 1f)
            }

            rotationY = (rotationY + ROTATION_SPEED * dt) % (Math.PI * 2)

            if (rotationY >= Math.PI) rotationY -= Math.PI * 2

            rotation.setAngleAxis(rotationY, 0.0, 1.0, 0.0)

            val prevElevation = elevation
            elevation = (elevation + hoverDirection * HOVER_SPEED * dt).coerceIn(-HOVER_AMPLITUDE, HOVER_AMPLITUDE)

            if (elevation <= -HOVER_AMPLITUDE || elevation >= HOVER_AMPLITUDE) {
                hoverDirection *= -1
            }

            position.setComponent(1, position.get(1) - prevElevation + elevation)
        }
    }

    private inner class YieldAnimation(private val manager: GbManager, private val anchor: GbAnchor) : Animatable {

        private var yieldDelay = YIELD_DELAY_SECONDS

        init {
            requiredYieldCount = glowStones.size
        }

        override fun updateAnimation(dt: Double, ctx: AnimationContext) {
            if (glowStones.isEmpty()) return

            yieldDelay -= dt

            if (yieldDelay > 0) return

            yieldDelay += YIELD_DELAY_SECONDS

            val glowStone = glowStones.removeFirst()
            glowStone.yieldInto(anchor, manager)

            val pos = worldTranslation()
            ctx.world().playSound(null, pos.x(), pos.y(), pos.z(), SoundEvents.CRAFTER_CRAFT, SoundSource.HOSTILE, 1f, 1.25f)
        }
    }
}
