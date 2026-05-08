package work.lclpnet.ap2.game.anvil_fall

import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import work.lclpnet.gaco.ds.BlockBox
import java.util.*
import kotlin.math.roundToInt

class AnvilFallSetup(
    private val startX: Int,
    private val startY: Int,
    private val startZ: Int,
    private val width: Int,
    private val length: Int,
    private val height: ByteArray,
    private val random: Random,
) {
    private fun getIndexX(x: Int) = x - startX
    private fun getIndexZ(z: Int) = z - startZ
    private fun getIndexFromIndices(ix: Int, iz: Int) = iz * width + ix
    private fun getIndex(x: Int, z: Int) = getIndexFromIndices(getIndexX(x), getIndexZ(z))
    private fun getRandomY(i: Int) = startY + random.nextInt(height[i].toInt())

    fun getRandomPosition(targetX: Int, targetZ: Int, range: Double): BlockPos {
        val x = random.nextGaussian(targetX.toDouble(), range).roundToInt()
        val z = random.nextGaussian(targetZ.toDouble(), range).roundToInt()

        if (x < startX || x >= startX + width || z < startZ || z >= startZ + length) {
            return getRandomPosition()
        }

        return BlockPos(x, getRandomY(getIndex(x, z)), z)
    }

    fun getRandomPosition(): BlockPos {
        var i: Int
        var j = 0

        do {
            i = random.nextInt(height.size)
        } while (height[i] <= 0 && j++ < 16)

        val x: Int
        val y: Int
        val z: Int

        if (height[i] <= 0) {
            x = (2 * startX + width - 1) / 2
            y = startY
            z = (2 * startZ + length - 1) / 2
        } else {
            x = startX + i % width
            y = getRandomY(i)
            z = startZ + i / width
        }

        return BlockPos(x, y, z)
    }

    fun getRandomPositionAt(x: Int, z: Int): BlockPos {
        val i = getIndex(x, z)

        if (i < 0 || i >= height.size || height[i] <= 0) {
            return getRandomPosition(x, z, 2.0)
        }

        return BlockPos(x, getRandomY(i), z)
    }

    companion object {
        fun scanWorld(world: BlockGetter, box: BlockBox, random: Random): AnvilFallSetup {
            val min = box.min()
            val max = box.max()

            val xMin = min.x; val yMin = min.y; val zMin = min.z
            val xMax = max.x; val yMax = max.y; val zMax = max.z
            val width = xMax - xMin + 1
            val length = zMax - zMin + 1

            val pos = BlockPos.MutableBlockPos()
            val heights = ByteArray(width * length)

            for (x in xMin..xMax) {
                for (z in zMin..zMax) {
                    var heightCount = 0

                    var y = yMin
                    while (y <= yMax && heightCount < Byte.MAX_VALUE) {
                        pos.set(x, y, z)
                        val state = world.getBlockState(pos)
                        if (!state.getCollisionShape(world, pos).isEmpty) break
                        heightCount++
                        y++
                    }

                    if (heightCount < 0) heightCount = 0

                    val ix = x - xMin
                    val iz = z - zMin
                    heights[iz * width + ix] = heightCount.toByte()
                }
            }

            return AnvilFallSetup(xMin, yMin, zMin, width, length, heights, random)
        }
    }
}
