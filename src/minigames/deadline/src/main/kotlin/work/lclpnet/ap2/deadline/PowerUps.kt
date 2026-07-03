package work.lclpnet.ap2.deadline

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import work.lclpnet.gaco.scene.Scene
import work.lclpnet.gaco.scene.ServerWorldMountContext
import work.lclpnet.gaco.scene.`object`.BlockDisplayObject

private const val PICKUP_RADIUS = 1.0 // how close a rider needs to get to a pickup to collect it
private const val MARKER_SIZE = 0.5   // edge length of the marker cube in blocks

/**
 * Power-up pickups at the map's power-up spawn points, collected by riding into them.
 */
class PowerUps(level: ServerLevel) {

    private val scene = Scene(ServerWorldMountContext(level))
    private val pickups = HashMap<BlockPos, BlockDisplayObject>()

    fun spawn(positions: List<BlockPos>) {
        for (pos in positions) {
            if (pos in pickups) continue

            val marker = BlockDisplayObject(scene, Blocks.SEA_LANTERN.defaultBlockState())
            // center the shrunk marker inside its block position
            val margin = (1 - MARKER_SIZE) / 2
            marker.scale.set(MARKER_SIZE)
            marker.position.set(pos.x + margin, pos.y + margin, pos.z + margin)
            scene.add(marker)
            pickups[pos] = marker
        }
    }

    /** Collects the pickup the given position is close to, if any. Returns true when one was collected. */
    fun collect(position: Vec3): Boolean {
        val pos = pickups.keys.firstOrNull {
            position.distanceToSqr(Vec3.atCenterOf(it)) <= PICKUP_RADIUS * PICKUP_RADIUS
        } ?: return false

        pickups.remove(pos)?.detach()
        return true
    }
}
