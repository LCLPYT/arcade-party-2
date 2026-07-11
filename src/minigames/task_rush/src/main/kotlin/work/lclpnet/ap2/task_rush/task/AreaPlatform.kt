package work.lclpnet.ap2.task_rush.task

import com.mojang.math.Transformation
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.DyeColor
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.minecraft.world.waypoints.WaypointStyleAssets
import org.joml.Matrix4f
import work.lclpnet.ap2.ext.mc.setBlock
import work.lclpnet.ap2.impl.util.world.ChunkPersistence
import work.lclpnet.kibu.access.entity.ArmorStandAccess
import work.lclpnet.kibu.access.entity.EntityUtil
import java.util.*

/**
 * A 3x3x3 clearing with a 3x1x3 bedrock platform one block below, a glowing block display marking the
 * platform and a temporary waypoint pointing to its center.
 *
 * The clearing spans [base].x-1..[base].x+1 horizontally and [base].y..[base].y+2 vertically (set to air),
 * the bedrock floor sits at [base].y-1. Call [remove] to discard the marker entities.
 */
class AreaPlatform private constructor(
    private val entities: List<Entity>,
    private val chunkPersistence: ChunkPersistence,
) {

    fun remove() {
        entities.forEach(Entity::discard)
    }

    companion object {

        private const val WAYPOINT_RANGE = 500.0

        fun create(level: ServerLevel, base: BlockPos, color: DyeColor, chunkPersistence: ChunkPersistence): AreaPlatform {
            buildPlatform(level, base)

            val display = createDisplay(level, base, color)
            val waypoint = createWaypoint(level, base, color.textureDiffuseColor)

            level.addFreshEntity(display)
            level.addFreshEntity(waypoint)
            level.waypointManager.trackWaypoint(waypoint)

            return AreaPlatform(listOf(display, waypoint), chunkPersistence)
        }

        private fun buildPlatform(level: ServerLevel, base: BlockPos) {
            val pos = BlockPos.MutableBlockPos()

            for (dx in -1..1) {
                for (dz in -1..1) {
                    level.setBlock(pos.set(base.x + dx, base.y - 1, base.z + dz), Blocks.BEDROCK)

                    for (dy in 0..2) {
                        level.setBlock(pos.set(base.x + dx, base.y + dy, base.z + dz), Blocks.AIR)
                    }
                }
            }
        }

        private fun createDisplay(level: ServerLevel, base: BlockPos, color: DyeColor): Display.BlockDisplay {
            val marker = Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level)
            val margin = 0.015f

            marker.blockState = Blocks.BEDROCK.defaultBlockState()
            marker.setPosRaw(base.x - 1.0 + margin, base.y - 1.0 + margin, base.z - 1.0 + margin)
            marker.setTransformation(Transformation(Matrix4f().scale(3f - 2 * margin, 1f - 2 * margin, 3f - 2 * margin)))
            marker.glowColorOverride = color.textureDiffuseColor
            marker.setGlowingTag(true)

            return marker
        }

        private fun createWaypoint(level: ServerLevel, base: BlockPos, color: Int): ArmorStand {
            val marker = ArmorStand(EntityTypes.ARMOR_STAND, level)
            marker.setPos(Vec3(base.x + 0.5, base.y.toDouble(), base.z + 0.5))
            ArmorStandAccess.setSmall(marker, true)
            ArmorStandAccess.setMarker(marker, true)
            marker.isInvisible = true

            val icon = marker.waypointIcon()
            icon.color = Optional.of(color)
            icon.style = WaypointStyleAssets.DEFAULT
            EntityUtil.setAttribute(marker, Attributes.WAYPOINT_TRANSMIT_RANGE, WAYPOINT_RANGE)

            return marker
        }
    }
}
