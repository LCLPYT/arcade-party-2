package work.lclpnet.ap2.impl.util.collision;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.impl.util.BlockBox;

import static org.junit.jupiter.api.Assertions.*;

class UnionColliderTest {

    @Test
    void collidesWith() {
        var shape = UnionCollider.union(new BlockBox(0, 0, 0, 1, 1, 1),
                new BlockBox(1, 1, 1, 2, 2, 2));

        assertTrue(shape.collidesWith(0, 0, 0));
        assertTrue(shape.collidesWith(1, 1, 1));
        assertTrue(shape.collidesWith(2, 2, 2));
        assertFalse(shape.collidesWith(0, 2, 2));
        assertFalse(shape.collidesWith(2, 2, 0));
    }

    @Test
    void min() {
        var shape = UnionCollider.union(new BlockBox(0, 0, 0, 1, 1, 1),
                new BlockBox(1, 1, 1, 2, 2, 2));

        assertEquals(new BlockPos(0, 0, 0), shape.min());
    }

    @Test
    void max() {
        var shape = UnionCollider.union(new BlockBox(0, 0, 0, 1, 1, 1),
                new BlockBox(1, 1, 1, 2, 2, 2));

        assertEquals(new BlockPos(2, 2, 2), shape.max());
    }
}