package work.lclpnet.ap2.game.knockout.util;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SquareInwardsIteratorTest {

    @Test
    void inwardsIterator() {
        var it = new SquareInwardsIterator(2, 0, 0, 0, 0);

        var list = new ArrayList<BlockPos>(25);

        while (it.hasNext()) {
            list.add(it.next().toImmutable());
        }

        assertEquals(25, list.size());

        var expected = new ArrayList<BlockPos>(25);
        expected.add(new BlockPos(-2, 0, -2));
        expected.add(new BlockPos(-1, 0, -2));
        expected.add(new BlockPos( 0, 0, -2));
        expected.add(new BlockPos( 1, 0, -2));
        expected.add(new BlockPos( 2, 0, -2));

        expected.add(new BlockPos( 2, 0, -1));
        expected.add(new BlockPos( 2, 0,  0));
        expected.add(new BlockPos( 2, 0,  1));
        expected.add(new BlockPos( 2, 0,  2));

        expected.add(new BlockPos( 1, 0,  2));
        expected.add(new BlockPos( 0, 0,  2));
        expected.add(new BlockPos(-1, 0,  2));
        expected.add(new BlockPos(-2, 0,  2));

        expected.add(new BlockPos(-2, 0,  1));
        expected.add(new BlockPos(-2, 0,  0));
        expected.add(new BlockPos(-2, 0, -1));

        expected.add(new BlockPos(-1, 0, -1));
        expected.add(new BlockPos( 0, 0, -1));
        expected.add(new BlockPos( 1, 0, -1));

        expected.add(new BlockPos( 1, 0,  0));
        expected.add(new BlockPos( 1, 0,  1));

        expected.add(new BlockPos( 0, 0,  1));
        expected.add(new BlockPos(-1, 0,  1));

        expected.add(new BlockPos(-1, 0,  0));

        expected.add(new BlockPos( 0, 0,  0));

        assertEquals(expected, list);
    }
}