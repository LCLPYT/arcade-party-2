package work.lclpnet.ap2.game.paintball.util

import net.minecraft.core.Direction
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import work.lclpnet.ap2.game.team.DyeTeamKey
import work.lclpnet.ap2.impl.util.RayCastUtil
import work.lclpnet.ap2.impl.util.math.MathUtil.applySpread
import work.lclpnet.gaco.scene.Object3d
import work.lclpnet.gaco.scene.Scene
import work.lclpnet.gaco.scene.animation.Animatable
import work.lclpnet.gaco.scene.animation.AnimationContext
import java.util.*
import java.util.function.Predicate
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

private const val MAX_AGE_TICKS = 160
private const val TRAIL_PARTICLE_SPACING = 0.4
private const val UPWARD_SPEED_SCALE = 0.65
private const val MIN_TRAIL_SPACING = 0.5

/**
 * A non-physics ink projectile.
 * A single shot spawns one [InkProjectile] that owns a cluster of independently moving ink blobs (spheres).
 * Each blob flies with momentum (a gravity arc), is swept for collision against terrain and enemies every tick,
 * and leaves a paint splat on the first surface it hits.
 * The projectile detaches once all of its blobs are gone.
 */
class InkProjectile(
    scene: Scene,
    private val world: ServerLevel,
    val settings: InkSettings,
    private val owner: UUID,
    private val teamKey: DyeTeamKey,
    private val manager: PaintGunManager,
    private val enemyFilter: Predicate<Entity>,
    spawnPos: Vec3,
    baseDir: Vec3,
    random: Random
) : Object3d(scene), Animatable {

    private val blobs = ArrayList<Blob>()

    init {
        position.set(spawnPos.x, spawnPos.y, spawnPos.z)

        val effectiveSpeed = settings.speed * pitchScale(baseDir.y)

        repeat(settings.blobCount) {
            val dir = applySpread(baseDir, settings.blobSpread, random)
            val jitter = 0.9 + random.nextDouble() * 0.2
            blobs.add(Blob(spawnPos, dir.scale(effectiveSpeed * jitter)))
        }
    }

    override fun updateAnimation(dt: Double, ctx: AnimationContext) {
        val iterator = blobs.iterator()

        while (iterator.hasNext()) {
            val blob = iterator.next()

            val prev = blob.pos
            blob.vel = blob.vel.add(0.0, -settings.gravity * dt, 0.0)

            var step = blob.vel.scale(dt)
            var dist = step.length()

            // clamp the step so the blob never travels past its configured range, regardless of speed
            var reachedRange = false
            val remaining = settings.range - blob.traveled

            if (dist >= remaining) {
                reachedRange = true

                if (dist > 1e-6) {
                    step = step.scale(remaining / dist)
                }

                dist = remaining
            }

            val next = prev.add(step)

            if (dist > 1e-6) {
                val impact = collide(prev, step.scale(1.0 / dist), dist)

                if (impact != null) {
                    finishTrail(blob, impact)
                    iterator.remove()
                    continue
                }
            }

            blob.pos = next
            blob.traveled += dist
            blob.age++

            emitTrailParticles(prev, next, dist)

            if (reachedRange || blob.age >= MAX_AGE_TICKS) {
                finishTrail(blob, next)
                iterator.remove()
            } else {
                tickTrail(blob, dt)
            }
        }

        if (blobs.isEmpty()) {
            detach()
        }
    }

    /**
     * Sphere-casts a blob's per-tick motion against terrain and enemies and paints/hurts on the first contact.
     * Returns the impact position if the blob hit something (and should be retired), or null otherwise.
     */
    private fun collide(from: Vec3, dir: Vec3, dist: Double): Vec3? {
        val blockHit = RayCastUtil.raycastBlocks(
            world,
            from,
            dir,
            dist,
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            CollisionContext.empty()
        )

        val blockDistSq = if (blockHit.type == HitResult.Type.BLOCK) {
            from.distanceToSqr(blockHit.location)
        } else {
            Double.MAX_VALUE
        }

        val entityHit = raycastEntity(from, dir, dist)
        val entityDistSq = entityHit?.let { from.distanceToSqr(it.second) } ?: Double.MAX_VALUE

        if (blockHit.type == HitResult.Type.BLOCK && blockDistSq <= entityDistSq) {
            manager.splat(owner, blockHit.location, settings.splatRadius, settings.deficitPaintBoost)
            return blockHit.location
        }

        if (entityHit != null) {
            manager.inkHitEntity(owner, entityHit.first, settings.damage, settings.deficitPaintBoost)
            return entityHit.second
        }

        return null
    }

    /**
     * Extends the droplet trail up to the given end position before the blob is retired, so the
     * trail reaches the actual impact or range endpoint instead of stopping at the last drop.
     * Only affects guns that lay a filled trail (subdivisions greater than zero).
     */
    private fun finishTrail(blob: Blob, end: Vec3) {
        val trail = settings.trail

        if (trail.trailTicks == Int.MAX_VALUE || trail.subdivisions <= 0) return

        blob.pos = end
        dropTrail(blob)
    }

    /**
     * Sweeps the blob's motion segment against enemy bounding boxes, each inflated by the blob
     * radius, so the blob collides as a sphere rather than an infinitely thin ray.
     */
    private fun raycastEntity(from: Vec3, dir: Vec3, dist: Double): Pair<Entity, Vec3>? {
        val radius = settings.blobRadius
        val end = from.add(dir.scale(dist))
        val box = AABB(from, end).inflate(radius)

        var nearest: Entity? = null
        var nearestHit: Vec3? = null
        var nearestDistSq = Double.MAX_VALUE

        for (entity in world.getEntities(null as Entity?, box, enemyFilter)) {
            val inflated = entity.boundingBox.inflate(radius)
            val hit = if (inflated.contains(from)) from else inflated.clip(from, end).orElse(null) ?: continue

            val distSq = from.distanceToSqr(hit)

            if (distSq < nearestDistSq) {
                nearest = entity
                nearestHit = hit
                nearestDistSq = distSq
            }
        }

        return if (nearest != null && nearestHit != null) nearest to nearestHit else null
    }

    private fun emitTrailParticles(from: Vec3, to: Vec3, dist: Double) {
        val size = settings.blobRadius.toFloat()
        val particle = DustParticleOptions(teamKey.color, size * 2)

        var traveled = 0.0

        while (traveled <= dist) {
            val t = if (dist > 1e-6) traveled / dist else 0.0
            val x = from.x + (to.x - from.x) * t
            val y = from.y + (to.y - from.y) * t
            val z = from.z + (to.z - from.z) * t

            world.sendParticles(particle, x, y, z, 1, 0.0, 0.0, 0.0, 0.0)

            traveled += TRAIL_PARTICLE_SPACING
        }
    }

    private fun tickTrail(blob: Blob, dt: Double) {
        val trail = settings.trail

        if (blob.droplets >= trail.maxDroplets || trail.trailTicks == Int.MAX_VALUE) return

        if (abs(blob.trailTimer) <= 1e-6) {
            blob.trailTimer = trail.trailTicks / 20.0
            dropTrail(blob)
        } else {
            blob.trailTimer = max(0.0, blob.trailTimer - dt)
        }
    }

    private fun dropTrail(blob: Blob) {
        val pos = blob.pos
        val trail = settings.trail
        val last = blob.lastSplitPos

        if (last != null && trail.subdivisions > 0) {
            val diff = pos.subtract(last)
            val segmentLength = diff.length()

            // derive the droplet count from the segment length so the droplets keep overlapping
            // at high speeds and the trail stays a solid line. The configured subdivisions act as a floor.
            val spacing = max(trail.dropletRadius.toDouble(), MIN_TRAIL_SPACING)
            val count = max(trail.subdivisions, floor(segmentLength / spacing).toInt())

            val frac = 1.0 / (count + 1)

            for (i in 1..count) {
                dropletAt(blob, last.add(diff.scale(i * frac)))
            }
        }

        blob.lastSplitPos = pos
        dropletAt(blob, pos)
    }

    private fun dropletAt(blob: Blob, start: Vec3) {
        if (blob.droplets >= settings.trail.maxDroplets) return

        blob.droplets++

        val hit = RayCastUtil.raycastBlocks(
            world, start, Direction.DOWN.unitVec3, 10.0,
            ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()
        )

        if (hit.type != HitResult.Type.BLOCK) return

        manager.splat(owner, hit.location, settings.trail.dropletRadius, settings.deficitPaintBoost)
    }

    private fun pitchScale(dirY: Double): Double =
        1.0 + (UPWARD_SPEED_SCALE - 1.0) * max(0.0, dirY)

    private class Blob(var pos: Vec3, var vel: Vec3) {
        var age = 0
        var traveled = 0.0
        var trailTimer = 0.0
        var droplets = 0
        // seed with the spawn position so the trail is filled in from the muzzle on the first drop
        var lastSplitPos: Vec3? = pos
    }
}
