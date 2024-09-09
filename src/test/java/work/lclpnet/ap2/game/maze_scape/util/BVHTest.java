package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.impl.util.BlockBox;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BVHTest {

    @Test
    void testSingleBoxIntersection() {
        var bvh = new BVH(List.of(box(0, 2)));
        assertTrue(bvh.intersects(box(1, 3)));
    }

    @Test
    void testNoIntersection() {
        var bvh = new BVH(List.of(box(0, 2), box(5, 7)));
        assertFalse(bvh.intersects(box(3, 4)));
    }

    @Test
    void testMultipleBoxIntersection() {
        var bvh = new BVH(List.of(box(0, 2), box(3, 5)));
        assertTrue(bvh.intersects(box(1, 4)));
    }

    @Test
    void testEdgeTouchingIntersection() {
        var bvh = new BVH(List.of(box(0, 2), box(3, 5)));
        assertTrue(bvh.intersects(box(2, 3)));
    }

    @Test
    void testContainedBoxIntersection() {
        BVH bvh = new BVH(List.of(box(0, 5)));
        assertTrue(bvh.intersects(box(1, 2)));
    }

    @Test
    void testNestedBVHIntersection() {
        var bvh = new BVH(List.of(box(0, 2), box(3, 5), box(6, 8)));
        assertTrue(bvh.intersects(box(4, 7)));
    }

    private static @NotNull BlockBox box(int i, int j) {
        return new BlockBox(new BlockPos(i, i, i), new BlockPos(j, j, j));
    }
}