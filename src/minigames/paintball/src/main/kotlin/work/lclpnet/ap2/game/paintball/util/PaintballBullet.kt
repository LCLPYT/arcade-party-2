package work.lclpnet.ap2.game.paintball.util

import com.jme3.math.Vector3f
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import org.joml.Vector3d
import work.lclpnet.ap2.impl.util.RayCastUtil
import work.lclpnet.ap2.impl.util.debug.DebugController
import work.lclpnet.gaco.scene.MountContext
import work.lclpnet.gaco.scene.Scene
import work.lclpnet.gaco.scene.animation.AnimationContext
import work.lclpnet.kibu.scheduler.Ticks
import java.util.*
import kotlin.math.abs
import kotlin.math.max

private const val FADE_TIME_SECONDS = 1.5
private const val MAX_TRAVEL_DIST = 256.0
private val MAX_AGE = Ticks.seconds(8)
private const val DEBUG_SPLITTING = false

class PaintballBullet(
    scene: Scene,
    blockState: BlockState,
    world: ServerLevel,
    val settings: PaintGun.BulletSettings,
    private val paintManager: PaintGunManager,
    private val debugController: DebugController
) : PaintballProjectile(scene, blockState, world) {

    var owner: UUID? = null
    private val initialScale = Vector3d()
    private val startPos = Vector3d()
    private var fadeTime = -1.0
    private var despawnTimer = -1.0
    var painting = true
    private var hits = 0
    private var splitTimer = 0.0
    private var splits = 0
    var playerContact = false
    private var lastSplitPos: Vec3? = null

    init {
        rigidBody.setMass(settings.mass)

        if (settings.power >= 20) {
            rigidBody.setCcdMotionThreshold(1e-4f)
            rigidBody.setCcdSweptSphereRadius(0.1f)
        }
    }

    override fun mount(ctx: MountContext) {
        super.mount(ctx)
        startPos.set(position)
    }

    override fun updateAnimation(dt: Double, ctx: AnimationContext) {
        super.updateAnimation(dt, ctx)

        if (ageTicks >= MAX_AGE) {
            detach()
            return
        }

        if (despawnTimer >= 0) {
            despawnTimer = max(despawnTimer - dt, 0.0)

            if (despawnTimer <= 0) {
                despawnTimer = -1.0
                startFading()
            }
        }

        if (isFading()) {
            fadeTime = max(fadeTime - dt, 0.0)
            scale.set(initialScale).mul(fadeTime / FADE_TIME_SECONDS)

            if (fadeTime <= 0) {
                fadeTime = -1.0
                detach()
            }
        }

        if (position.y < world.minY - 40 || position.distanceSquared(startPos) > MAX_TRAVEL_DIST * MAX_TRAVEL_DIST) {
            detach()
        }

        tickSplitting(dt)
    }

    private fun tickSplitting(dt: Double) {
        if (splits >= settings.split.maxSplits || isFading() || !painting) return
        if (settings.split.splitTicks == Int.MAX_VALUE) return

        if (abs(splitTimer) <= 1e-6) {
            splitTimer = settings.split.splitTicks / 20.0
            split()
        } else {
            splitTimer = max(0.0, splitTimer - dt)
        }
    }

    private fun split() {
        val loc = rigidBody.getFrame().getLocation(Vector3f(), 1f)
        val pos = Vec3(loc.x.toDouble(), loc.y.toDouble(), loc.z.toDouble())

        val subdivisions = settings.split.splitSubdivisions
        val lastSplitPos = lastSplitPos

        if (lastSplitPos != null && subdivisions > 0) {
            val frac = 1.0 / (subdivisions + 1)
            val diff = pos.subtract(lastSplitPos)

            for (i in 1..subdivisions) {
                splitAt(lastSplitPos.add(diff.scale(i * frac)))
            }
        }

        this.lastSplitPos = pos
        splitAt(pos)
    }

    private fun splitAt(start: Vec3) {
        if (splits >= settings.split.maxSplits) return

        splits++

        val dir = Direction.DOWN.unitVec3

        if (DEBUG_SPLITTING) {
            debugController.renderer().ifPresent { renderer ->
                renderer.marker(start, Blocks.DIAMOND_BLOCK.defaultBlockState(), 0x5555ff)
                renderer.arrow(start, dir, Blocks.BLACK_CONCRETE.defaultBlockState())
            }
        }

        val hit = RayCastUtil.raycastBlocks(
            world,
            start,
            dir,
            10.0,
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            CollisionContext.empty()
        )

        if (hit.type != HitResult.Type.BLOCK) return

        val pos = (hit as BlockHitResult).location
        paintManager.paintAt(this, pos.x, pos.y, pos.z, settings.split.splitPaintRadius.toDouble(), false)
    }

    fun startDespawnTimer() {
        if (despawnTimer >= 0 || isFading()) return

        despawnTimer = settings.despawnSeconds
    }

    fun isFading() = fadeTime >= 0

    fun startFading() {
        if (isFading()) return

        fadeTime = FADE_TIME_SECONDS
        initialScale.set(scale)
        painting = false
    }

    fun onHit() {
        if (++hits == settings.maxHits.toInt()) {
            startFading()
        }
    }
}
