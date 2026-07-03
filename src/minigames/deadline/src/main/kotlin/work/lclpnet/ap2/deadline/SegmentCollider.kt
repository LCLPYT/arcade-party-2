package work.lclpnet.ap2.deadline

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.floor

// grid cell edge length in blocks, used for looking up nearby segments quickly
private const val CELL_SIZE = 4.0

// how far a trail segment reaches from its center line
private const val HALF_HEIGHT = 0.5
private const val HALF_THICKNESS = 0.0625

// the number of freshest segments that are not lethal to themselves
private const val GRACE_SEGMENTS = 3

/**
 * Collision index for the trail segments: a spatial hash grid for the broad phase,
 * and a distance check against the segment's center line for the narrow phase.
 */
class SegmentCollider {

    private val cells = HashMap<Long, MutableList<Segment>>()
    private val owned = HashMap<UUID, ArrayDeque<Segment>>()

    fun add(owner: UUID, start: Vec3, end: Vec3) {
        val segments = owned.getOrPut(owner) { ArrayDeque() }
        // the pane is anchored at its base, so the center line runs at half height
        val segment = Segment(start.add(0.0, HALF_HEIGHT, 0.0), end.add(0.0, HALF_HEIGHT, 0.0), owner)
        segments.addLast(segment)
        forEachCell(segment.bounds()) { key ->
            cells.getOrPut(key) { mutableListOf() }.add(segment)
        }
    }

    fun remove(owner: UUID) {
        val segments = owned.remove(owner) ?: return
        segments.forEach(::dropFromCells)
    }

    fun removeOldest(owner: UUID) {
        val oldest = owned[owner]?.removeFirstOrNull() ?: return
        dropFromCells(oldest)
    }

    fun collides(box: AABB, rider: UUID): Boolean {
        var hit = false
        forEachCell(box) { key ->
            if (!hit && cells[key]?.any { isLethalTo(it, rider) && it.collidesWith(box) } == true) hit = true
        }
        return hit
    }

    private fun isLethalTo(segment: Segment, rider: UUID): Boolean {
        // other riders segments are always lethal
        if (segment.owner != rider) return true

        val segments = owned[rider] ?: return true

        // owned segments within the grace period are not lethal
        for (i in (segments.size - GRACE_SEGMENTS).coerceAtLeast(0) until segments.size) {
            if (segments[i] === segment) return false
        }

        return true
    }

    private fun dropFromCells(segment: Segment) {
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

    private class Segment(val a: Vec3, val b: Vec3, val owner: UUID) {

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
