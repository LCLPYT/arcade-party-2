package work.lclpnet.ap2.game.anvil_fall

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import java.util.*

class AnvilFallSetupTest {

    @Test
    fun getRandomPosition() {
        val random = Random(12345)

        val width = 10
        val height = 10
        val startX = 5
        val startZ = 5

        val heights = ByteArray(width * height) { 1 }
        val setup = AnvilFallSetup(startX, 0, startZ, width, height, heights, random)

        val counts = IntArray(heights.size)

        for (i in 0 until 1000) {
            val pos = setup.getRandomPosition(startX + 9, startZ, 2.0)

            val ix = pos.x - startX
            val iz = pos.z - startZ
            val idx = iz * width + ix

            counts[idx]++
        }

        val expected = intArrayOf(
            12,  4,  8,  9,  9, 14, 14, 27, 37, 38,
             6,  3,  6,  6,  8, 12, 20, 42, 36, 35,
             3, 11,  9,  5,  5, 11, 16, 21, 21, 32,
            11,  3,  3,  4, 10,  9,  5, 14, 20, 23,
             4,  6,  6,  7,  5,  8,  8, 11, 16, 20,
             4, 10,  3,  8,  9,  6,  8,  9,  8,  5,
             6,  8,  6,  7,  6,  9, 13,  5, 10,  5,
             8,  8,  5, 11, 10,  6,  5,  7,  5,  2,
             6,  8,  5,  1,  8,  4,  4,  8,  9,  7,
             3,  9,  5,  6,  5,  5,  4,  6,  6,  6,
        )

        assertArrayEquals(expected, counts)
    }

    @Suppress("unused")
    private fun printMatrix(matrix: IntArray, height: Int, width: Int) {
        for (z in 0 until height) {
            val row = (0 until width).joinToString(" ") { x -> "%02d".format(matrix[z * width + x]) }
            println(row)
        }
    }
}
