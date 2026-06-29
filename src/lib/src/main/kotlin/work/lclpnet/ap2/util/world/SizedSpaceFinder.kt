package work.lclpnet.ap2.util.world

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.Shapes
import work.lclpnet.ap2.api.util.world.SpaceFinder
import java.util.*
import java.util.stream.StreamSupport
import kotlin.math.ceil
import kotlin.math.floor

class SizedSpaceFinder(
    private val blockView: BlockGetter,
    private val width: Float,
    private val height: Float,
    private val length: Float
) : SpaceFinder {

    override fun findSpaces(positions: Iterator<BlockPos>): List<Vec3> {

        val spliterator = Spliterators.spliteratorUnknownSize(positions, 0)

        @Suppress("UNCHECKED_CAST")
        return StreamSupport.stream(spliterator, false)
            .map { pos -> spaceAt(pos) }
            .filter { obj -> Objects.nonNull(obj) }
            .toList() as List<Vec3>
    }

    private fun spaceAt(pos: BlockPos): Vec3? {
        val minX = pos.x.toDouble()
        val minY = pos.y.toDouble()
        val minZ = pos.z.toDouble()
        val maxX = minX + 1
        val maxY = minY + 1
        val maxZ = minZ + 1

        // prefer the block center, then fall back to the edges
        val xs = doubleArrayOf(minX + 0.5, minX, maxX)
        val zs = doubleArrayOf(minZ + 0.5, minZ, maxZ)

        for (x in xs) for (z in zs) {
            var y = minY
            while (y <= maxY) {
                if (hasSpace(x, y, z)) return Vec3(x, y, z)
                y += 0.5
            }
        }

        return null
    }

    private fun hasSpace(x: Double, y: Double, z: Double): Boolean =
        hasSpaceAt(blockView, x, y, z, width.toDouble(), height.toDouble(), length.toDouble())

    companion object {

        @JvmStatic
        fun create(blockView: BlockGetter, entityType: EntityType<*>): SizedSpaceFinder {
            val dimensions = entityType.dimensions

            val width = ceil(dimensions.width().toDouble()).toInt()
            val height = ceil(dimensions.height().toDouble()).toInt()

            return SizedSpaceFinder(blockView, width.toFloat(), height.toFloat(), width.toFloat())
        }

        fun hasSpaceAt(
            level: BlockGetter,
            x: Double,
            y: Double,
            z: Double,
            width: Double,
            height: Double,
            length: Double
        ): Boolean {
            val halfWidth = width * 0.5
            val halfLength = length * 0.5

            val minX = x - halfWidth
            val minZ = z - halfLength
            val maxX = x + halfWidth
            val maxY = y + height
            val maxZ = z + halfLength

            val space = Shapes.create(minX, y, minZ, maxX, maxY, maxZ)

            return BlockPos.betweenClosedStream(
                floor(minX).toInt(),
                floor(y).toInt(),
                floor(minZ).toInt(),
                ceil(maxX).toInt(),
                ceil(maxY).toInt(),
                ceil(maxZ).toInt()
            ).noneMatch { pos ->
                val shape = level.getBlockState(pos).getCollisionShape(level, pos)
                    .move(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())

                Shapes.joinIsNotEmpty(shape, space, BooleanOp.AND)
            }
        }
    }
}