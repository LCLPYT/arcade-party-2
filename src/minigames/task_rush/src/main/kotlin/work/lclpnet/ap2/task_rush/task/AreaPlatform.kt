package work.lclpnet.ap2.task_rush.task

import com.mojang.math.Transformation
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.item.DyeColor
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import work.lclpnet.ap2.ext.mc.setBlock
import work.lclpnet.ap2.game.util.DynamicWaypoint
import work.lclpnet.ap2.impl.util.world.ChunkPersistence

/**
 * A 3x3x3 clearing with a 3x1x3 bedrock platform one block below, a glowing block display marking the
 * platform and a temporary waypoint pointing to its center.
 *
 * The clearing spans [base].x-1..[base].x+1 horizontally and [base].y..[base].y+2 vertically (set to air),
 * the bedrock floor sits at [base].y-1. Call [remove] to discard the marker.
 */
class AreaPlatform private constructor(
    private val display: Display.BlockDisplay,
    private val waypoint: DynamicWaypoint,
    private val chunkPersistence: ChunkPersistence,
) {

    fun remove() {
        display.discard()
        waypoint.untrack()
    }

    companion object {

        fun create(level: ServerLevel, base: BlockPos, color: DyeColor, chunkPersistence: ChunkPersistence): AreaPlatform {
            buildPlatform(level, base)

            val display = createDisplay(level, base, color)
            val waypoint = createWaypoint(level, base, color.textureDiffuseColor)

            level.addFreshEntity(display)
            waypoint.track()

            return AreaPlatform(display, waypoint, chunkPersistence)
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

        private fun createWaypoint(level: ServerLevel, base: BlockPos, color: Int): DynamicWaypoint {
            val pos = Vec3(base.x + 0.5, base.y.toDouble(), base.z + 0.5)

            return DynamicWaypoint(level, color = color) { pos }
        }
    }
}
