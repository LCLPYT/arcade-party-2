package work.lclpnet.ap2.deadline.trail

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.floor

// grid cell edge length in blocks, used for looking up nearby segments quickly
private const val CELL_SIZE = 4.0

// how far a trail segment reaches from its center line
private const val HALF_HEIGHT = 0.5
private const val HALF_THICKNESS = 0.0625

// the number of freshest segments that are not lethal to themselves
private const val GRACE_SEGMENTS = 3

// distance between sampled positions along a tick's movement. must stay below the collision
// window (pane thickness + player width), so a fast rider cannot pass a trail between two samples
private const val SAMPLE_SPACING = 0.5

/**
 * Collision index for the trail segments: a spatial hash grid for the broad phase,
 * and a distance check against the segment's center line for the narrow phase.
 *
 * The collider is the single owner of a rider's segments. Each segment carries a caller
 * attachment (e.g. its display), which is handed back when the segment is removed, so the
 * collision data can never drift apart from what the attachments represent.
 */
class SegmentCollider<T> {

    private val cells = HashMap<Long, MutableList<Segment<T>>>()
    private val owned = HashMap<UUID, ArrayDeque<Segment<T>>>()

    fun add(owner: UUID, start: Vec3, end: Vec3, attachment: T) {
        val segments = owned.getOrPut(owner) { ArrayDeque() }
        // the pane is anchored at its base, so the center line runs at half height
        val segment = Segment(start.add(0.0, HALF_HEIGHT, 0.0), end.add(0.0, HALF_HEIGHT, 0.0), owner, attachment)
        segments.addLast(segment)
        forEachCell(segment.bounds()) { key ->
            cells.getOrPut(key) { mutableListOf() }.add(segment)
        }
    }

    fun size(owner: UUID): Int = owned[owner]?.size ?: 0

    /** Removes all segments of the owner and returns their attachments. */
    fun remove(owner: UUID): List<T> {
        val segments = owned.remove(owner) ?: return emptyList()
        segments.forEach(::dropFromCells)
        return segments.map { it.attachment }
    }

    /** Removes the oldest segment of the owner and returns its attachment. */
    fun removeOldest(owner: UUID): T? {
        val oldest = owned[owner]?.removeFirstOrNull() ?: return null
        dropFromCells(oldest)
        return oldest.attachment
    }

    /** The owner of the lethal segment the rider's hitbox touched anywhere along its movement of this tick, or null. */
    fun hit(box: AABB, movement: Vec3, rider: UUID): UUID? {
        // gather the nearby lethal segments only once, from the whole area swept by this tick's movement
        val candidates = mutableListOf<Segment<T>>()

        forEachCell(box.minmax(box.move(movement.scale(-1.0)))) { key ->
            cells[key]?.forEach { segment ->
                if (segment !in candidates && isLethalTo(segment, rider)) candidates.add(segment)
            }
        }

        if (candidates.isEmpty()) return null

        // check intermediate positions too, so fast riders cannot skip over a trail between two ticks
        val steps = ceil(movement.length() / SAMPLE_SPACING).toInt().coerceAtLeast(1)

        for (i in 0..steps) {
            val t = i.toDouble() / steps // 0 = position at the previous tick, 1 = current position
            val sampled = box.move(movement.scale(t - 1.0))
            val hits = candidates.filter { it.collidesWith(sampled) }

            if (hits.isEmpty()) continue

            // touching the own trail counts as a suicide, even if other trails are hit at the same
            // time, so that nobody is credited a kill the rider caused themselves
            return (hits.firstOrNull { it.owner == rider } ?: hits.first()).owner
        }

        return null
    }

    private fun isLethalTo(segment: Segment<T>, rider: UUID): Boolean {
        // other riders segments are always lethal
        if (segment.owner != rider) return true

        val segments = owned[rider] ?: return true

        // owned segments within the grace period are not lethal
        for (i in (segments.size - GRACE_SEGMENTS).coerceAtLeast(0) until segments.size) {
            if (segments[i] === segment) return false
        }

        return true
    }

    private fun dropFromCells(segment: Segment<T>) {
        forEachCell(segment.bounds()) { key ->
            val list = cells[key] ?: return@forEachCell
            list.remove(segment)
            if (list.isEmpty()) cells.remove(key)
        }
    }

    private inline fun forEachCell(box: AABB, action: (Long) -> Unit) {
        val minX = floor(box.minX / CELL_SIZE).toInt()
        val minY = floor(box.minY / CELL_SIZE).toInt()
        val minZ = floor(box.minZ / CELL_SIZE).toInt()
        val maxX = floor(box.maxX / CELL_SIZE).toInt()
        val maxY = floor(box.maxY / CELL_SIZE).toInt()
        val maxZ = floor(box.maxZ / CELL_SIZE).toInt()

        for (x in minX..maxX) for (y in minY..maxY) for (z in minZ..maxZ) {
            action(BlockPos.asLong(x, y, z))
        }
    }

    private class Segment<T>(val a: Vec3, val b: Vec3, val owner: UUID, val attachment: T) {

        fun bounds(): AABB = AABB(a, b).inflate(HALF_THICKNESS, HALF_HEIGHT, HALF_THICKNESS)

        fun collidesWith(box: AABB): Boolean {
            // closest point on the center line to the box center
            val center = box.center
            val ab = b.subtract(a)
            val t = (center.subtract(a).dot(ab) / ab.lengthSqr()).coerceIn(0.0, 1.0)
            val p = a.add(ab.scale(t))

            // the pane only spans half a block up and down from its center line
            if (p.y < box.minY - HALF_HEIGHT || p.y > box.maxY + HALF_HEIGHT) return false

            // horizontally, hit when the line comes closer to the box than the pane thickness
            val dx = p.x - p.x.coerceIn(box.minX, box.maxX)
            val dz = p.z - p.z.coerceIn(box.minZ, box.maxZ)
            return dx * dx + dz * dz <= HALF_THICKNESS * HALF_THICKNESS
        }
    }
}
