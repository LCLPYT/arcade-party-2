package work.lclpnet.ap2.deadline

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.DyeColor
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import work.lclpnet.gaco.scene.Scene
import work.lclpnet.gaco.scene.ServerWorldMountContext
import work.lclpnet.gaco.scene.`object`.BlockDisplayObject
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.sqrt

private const val SEGMENT_LENGTH = 1.0 // smallest trail segment length in blocks; larger means fewer displays
private const val MAX_SEGMENTS = 150 // how many segments a trail keeps before its tail starts to disappear

/**
 * Draws each rider's glowing glass-pane trail as stretched, heading-aligned block displays in a gaco scene.
 */
class LightTrail(level: ServerLevel) {

    private val scene = Scene(ServerWorldMountContext(level))
    private val anchor = HashMap<UUID, Vec3>()
    private val trails = HashMap<UUID, MutableList<BlockDisplayObject>>()
    private val collider = SegmentCollider()

    fun extend(uuid: UUID, position: Vec3, color: DyeColor) {
        val start = anchor[uuid]
        if (start == null) {
            anchor[uuid] = position
            return
        }
        if (position.distanceToSqr(start) < SEGMENT_LENGTH * SEGMENT_LENGTH) return
        placeSegment(uuid, start, position, color)
        collider.add(uuid, start, position)
        anchor[uuid] = position
        trim(uuid)
    }

    // the tail of the trail disappears once the segment limit is reached
    private fun trim(uuid: UUID) {
        val displays = trails[uuid] ?: return
        while (displays.size > MAX_SEGMENTS) {
            displays.removeFirst().detach()
            collider.removeOldest(uuid)
        }
    }

    private fun placeSegment(uuid: UUID, start: Vec3, end: Vec3, color: DyeColor) {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val dz = end.z - start.z
        val horiz = sqrt(dx * dx + dz * dz)
        val len = sqrt(horiz * horiz + dy * dy)
        val display = BlockDisplayObject(scene, paneState(color))


        // yaw then pitch: turn the pane to the heading, tilt it up the slope and keep it upright at all times
        display.rotation.identity().rotateY(atan2(dx, dz)).rotateX(-atan2(dy, horiz))
        display.scale.set(1.0, 1.0, len)

        // pivot at the pane's mid-height (0.5, 0.5) so consecutive segments meet at their center
        val offset = Vector3d(0.5, 0.5, 0.0).rotate(display.rotation)
        display.position.set(start.x - offset.x, start.y + 0.5 - offset.y, start.z - offset.z)
        display.isGlowing = true
        display.glowColorOverride = color.textureDiffuseColor
        scene.add(display)
        trails.getOrPut(uuid) { mutableListOf() }.add(display)
    }

    private fun paneState(color: DyeColor): BlockState =
        Blocks.STAINED_GLASS_PANE.pick(color).defaultBlockState()
            .setValue(BlockStateProperties.NORTH, true)
            .setValue(BlockStateProperties.SOUTH, true)

    fun collides(box: AABB, movement: Vec3, rider: UUID) = collider.collides(box, movement, rider)

    fun discard(uuid: UUID) {
        collider.remove(uuid)
        trails.remove(uuid)?.forEach { it.detach() }
        anchor.remove(uuid)
    }
}
