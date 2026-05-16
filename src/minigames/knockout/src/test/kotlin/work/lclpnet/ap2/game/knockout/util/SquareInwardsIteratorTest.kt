package work.lclpnet.ap2.game.knockout.util

import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SquareInwardsIteratorTest {

    @Test
    fun inwardsIterator() {
        val it = SquareInwardsIterator(2, 0, 0, 0, 0)

        val list = ArrayList<BlockPos>(25)

        while (it.hasNext()) {
            list.add(it.next().immutable())
        }

        assertEquals(25, list.size)

        val expected = ArrayList<BlockPos>(25)
        expected.add(BlockPos(-2, 0, -2))
        expected.add(BlockPos(-1, 0, -2))
        expected.add(BlockPos( 0, 0, -2))
        expected.add(BlockPos( 1, 0, -2))
        expected.add(BlockPos( 2, 0, -2))

        expected.add(BlockPos( 2, 0, -1))
        expected.add(BlockPos( 2, 0,  0))
        expected.add(BlockPos( 2, 0,  1))
        expected.add(BlockPos( 2, 0,  2))

        expected.add(BlockPos( 1, 0,  2))
        expected.add(BlockPos( 0, 0,  2))
        expected.add(BlockPos(-1, 0,  2))
        expected.add(BlockPos(-2, 0,  2))

        expected.add(BlockPos(-2, 0,  1))
        expected.add(BlockPos(-2, 0,  0))
        expected.add(BlockPos(-2, 0, -1))

        expected.add(BlockPos(-1, 0, -1))
        expected.add(BlockPos( 0, 0, -1))
        expected.add(BlockPos( 1, 0, -1))

        expected.add(BlockPos( 1, 0,  0))
        expected.add(BlockPos( 1, 0,  1))

        expected.add(BlockPos( 0, 0,  1))
        expected.add(BlockPos(-1, 0,  1))

        expected.add(BlockPos(-1, 0,  0))

        expected.add(BlockPos( 0, 0,  0))

        assertEquals(expected, list)
    }
}
