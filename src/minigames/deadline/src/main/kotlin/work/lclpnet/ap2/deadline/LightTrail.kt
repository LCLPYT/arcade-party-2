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
private const val DELAY_TICKS = 3 // how many ticks the trail lags behind, matching the client's mount interpolation

/**
 * Draws each rider's glowing glass-pane trail as stretched, heading-aligned block displays in a gaco scene.
 */
class LightTrail(level: ServerLevel, private val spec: TrailSpec) {

    private val scene = Scene(ServerWorldMountContext(level))
    private val recent = HashMap<UUID, ArrayDeque<Vec3>>()
    private val anchor = HashMap<UUID, Vec3>()
    private val collider = SegmentCollider<BlockDisplayObject>()
    private var maxSegments = spec.initialSegments
    private var age = 0

    // trails get longer the longer the game goes on
    fun tick() {
        if (maxSegments < spec.maxSegments && ++age % spec.growthInterval == 0) {
            maxSegments++
        }
    }

    fun extend(uuid: UUID, position: Vec3, color: DyeColor) {
        // clients render the ridden sheep a few ticks behind its server position, so the trail
        // follows an equally delayed position to keep its tip visually at the sheep
        val buffer = recent.getOrPut(uuid) { ArrayDeque() }
        buffer.addLast(position)
        if (buffer.size <= DELAY_TICKS) return
        val delayed = buffer.removeFirst()

        val start = anchor[uuid]
        if (start == null) {
            anchor[uuid] = delayed
            return
        }
        if (delayed.distanceToSqr(start) < SEGMENT_LENGTH * SEGMENT_LENGTH) return
        collider.add(uuid, start, delayed, placeSegment(start, delayed, color))
        anchor[uuid] = delayed
        trim(uuid)
    }

    // the tail of the trail disappears once the segment limit is reached
    private fun trim(uuid: UUID) {
        while (collider.size(uuid) > maxSegments) {
            collider.removeOldest(uuid)?.detach()
        }
    }

    private fun placeSegment(start: Vec3, end: Vec3, color: DyeColor): BlockDisplayObject {
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
        return display
    }

    private fun paneState(color: DyeColor): BlockState =
        Blocks.STAINED_GLASS_PANE.pick(color).defaultBlockState()
            .setValue(BlockStateProperties.NORTH, true)
            .setValue(BlockStateProperties.SOUTH, true)

    fun collides(box: AABB, movement: Vec3, rider: UUID) = collider.collides(box, movement, rider)

    fun discard(uuid: UUID) {
        collider.remove(uuid).forEach { it.detach() }
        anchor.remove(uuid)
        recent.remove(uuid)
    }
}
