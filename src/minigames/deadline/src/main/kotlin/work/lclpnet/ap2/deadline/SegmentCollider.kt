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
    private val owned = HashMap<UUID, MutableList<Segment>>()

    fun add(owner: UUID, start: Vec3, end: Vec3) {
        val segments = owned.getOrPut(owner) { mutableListOf() }
        // the pane is anchored at its base, so the center line runs at half height
        val segment = Segment(start.add(0.0, HALF_HEIGHT, 0.0), end.add(0.0, HALF_HEIGHT, 0.0), owner, segments.size)
        segments.add(segment)
        forEachCell(segment.bounds()) { key ->
            cells.getOrPut(key) { mutableListOf() }.add(segment)
        }
    }

    fun remove(owner: UUID) {
        val segments = owned.remove(owner) ?: return
        for (segment in segments) {
            forEachCell(segment.bounds()) { key ->
                val list = cells[key] ?: return@forEachCell
                list.remove(segment)
                if (list.isEmpty()) cells.remove(key)
            }
        }
    }

    fun collides(box: AABB, rider: UUID): Boolean {
        // the rider's own freshest segments are still within the grace window and cannot be crashed into
        val graceStart = (owned[rider]?.size ?: 0) - GRACE_SEGMENTS

        var hit = false
        forEachCell(box) { key ->
            if (!hit && cells[key]?.any { it.isLethalTo(rider, graceStart) && it.collidesWith(box) } == true) hit = true
        }
        return hit
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

    private class Segment(val a: Vec3, val b: Vec3, val owner: UUID, val index: Int) {

        fun isLethalTo(rider: UUID, graceStart: Int) = owner != rider || index < graceStart

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
