package work.lclpnet.ap2.game.knockout.util

import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DistanceIteratorTest {

    @Test
    fun distanceIterator() {
        val distances = arrayOf(
            shortArrayOf(3, 2, 2, 2, 3),
            shortArrayOf(2, 1, 1, 1, 2),
            shortArrayOf(2, 1, 0, 1, 2),
            shortArrayOf(2, 1, 1, 1, 2),
            shortArrayOf(3, 2, 2, 2, 3)
        )

        val it = DistanceIterator(2, 0, 0, 0, 1, distances, 2)

        val list = ArrayList<BlockPos>(24)

        while (it.hasNext()) {
            list.add(it.next().immutable())
        }

        assertEquals(24, list.size)

        val expected = ArrayList<BlockPos>(24)
        expected.add(BlockPos(-1, 0, -2))
        expected.add(BlockPos(-1, 1, -2))
        expected.add(BlockPos(0, 0, -2))
        expected.add(BlockPos(0, 1, -2))
        expected.add(BlockPos(1, 0, -2))
        expected.add(BlockPos(1, 1, -2))

        expected.add(BlockPos(-2, 0, -1))
        expected.add(BlockPos(-2, 1, -1))
        expected.add(BlockPos(2, 0, -1))
        expected.add(BlockPos(2, 1, -1))

        expected.add(BlockPos(-2, 0, 0))
        expected.add(BlockPos(-2, 1, 0))
        expected.add(BlockPos(2, 0, 0))
        expected.add(BlockPos(2, 1, 0))

        expected.add(BlockPos(-2, 0, 1))
        expected.add(BlockPos(-2, 1, 1))
        expected.add(BlockPos(2, 0, 1))
        expected.add(BlockPos(2, 1, 1))

        expected.add(BlockPos(-1, 0, 2))
        expected.add(BlockPos(-1, 1, 2))
        expected.add(BlockPos(0, 0, 2))
        expected.add(BlockPos(0, 1, 2))
        expected.add(BlockPos(1, 0, 2))
        expected.add(BlockPos(1, 1, 2))

        assertEquals(expected, list)
    }
}
