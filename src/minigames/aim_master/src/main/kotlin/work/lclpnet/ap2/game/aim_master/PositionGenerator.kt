package work.lclpnet.ap2.game.aim_master

import net.minecraft.core.BlockPos
import work.lclpnet.gaco.ds.BlockBox
import java.util.Random
import kotlin.math.*

class PositionGenerator(
    private val radius: Int,
    private val offset: Int,
    private val upwardTilt: Double,
    private val ellipseFactor: Double,
    private val center: BlockPos,
    private val fov: Int,
    private val targetNumber: Int,
    private val targetMinDistance: Int
) {
    private fun generateBlockBox(): BlockBox {
        val pos1 = BlockPos(center.x + radius, center.y + radius, (center.z + offset) + radius)
        val pos2 = BlockPos(center.x - radius, center.y - radius, (center.z + offset) - radius)
        return BlockBox(pos1, pos2)
    }

    private fun generateCone(): ArrayList<BlockPos> {
        val validBlockPositions = ArrayList<BlockPos>()

        for (pos in generateBlockBox()) {
            val x = pos.x - center.x
            val y = pos.y - center.y
            val z = pos.z - (center.z + offset)

            val squaredDistance = x * x + y * y + z * z
            val radiusSquared = radius * radius

            val e = radius * (radius * 0.004 + 0.84)

            if (abs(squaredDistance - radiusSquared) <= e) {
                val a = radius.toDouble()
                val b = radius * ellipseFactor

                val distanceToEllipse = (x * x) / (a * a) + (y * y) / (b * b)

                if (distanceToEllipse <= 1) {
                    val angle = getAngle(x, y, z)

                    if (angle <= fov) {
                        validBlockPositions.add(pos.immutable())
                    }
                }
            }
        }

        return validBlockPositions
    }

    fun pickPositions(): ArrayList<BlockPos> {
        val cone = generateCone()
        val blockPositions = ArrayList<BlockPos>()
        val random = Random()

        while (blockPositions.size < targetNumber && cone.isNotEmpty()) {
            val randIndex = random.nextInt(cone.size)
            val selectedPos = cone.removeAt(randIndex)
            blockPositions.add(selectedPos)

            cone.removeAll { pos ->
                val dx = (pos.x - selectedPos.x).toDouble()
                val dy = (pos.y - selectedPos.y).toDouble()
                val dz = (pos.z - selectedPos.z).toDouble()
                sqrt(dx * dx + dy * dy + dz * dz) <= targetMinDistance
            }
        }

        return blockPositions
    }

    private fun getAngle(x: Int, y: Int, z: Int): Double {
        val vec = doubleArrayOf(x.toDouble(), y.toDouble(), z.toDouble())
        val viewDir = doubleArrayOf(0.0, upwardTilt, 1.0)
        return Math.toDegrees(
            acos(dotProduct(vec, viewDir) / (vecLen(vec) * vecLen(viewDir)))
        )
    }
}

private fun dotProduct(vec1: DoubleArray, vec2: DoubleArray): Double {
    var result = 0.0
    for (i in vec1.indices) result += vec1[i] * vec2[i]
    return result
}

private fun vecLen(vec: DoubleArray): Double = sqrt(vec[0] * vec[0] + vec[1] * vec[1] + vec[2] * vec[2])
