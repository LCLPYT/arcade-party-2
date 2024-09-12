package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.impl.util.BlockBox;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BVHTest {

    @Test
    void intersectsBox_singleBox() {
        var bvh = new BVH(List.of(box(0, 2)));

        assertTrue(bvh.intersects(box(1, 3)));
    }

    @Test
    void intersectsBox_noIntersection() {
        var bvh = new BVH(List.of(box(0, 2), box(5, 7)));

        assertFalse(bvh.intersects(box(3, 4)));
    }

    @Test
    void intersectsBox_multipleBoxes() {
        var bvh = new BVH(List.of(box(0, 2), box(3, 5)));

        assertTrue(bvh.intersects(box(1, 4)));
    }

    @Test
    void intersectsBox_edgeTouching() {
        var bvh = new BVH(List.of(box(0, 2), box(3, 5)));

        assertTrue(bvh.intersects(box(2, 3)));
    }

    @Test
    void intersectsBox_containedBox() {
        BVH bvh = new BVH(List.of(box(0, 5)));

        assertTrue(bvh.intersects(box(1, 2)));
    }

    @Test
    void intersectsBox_nestedBVH() {
        var bvh = new BVH(List.of(box(0, 2), box(3, 5), box(6, 8)));

        assertTrue(bvh.intersects(box(4, 7)));
    }

    @Test
    void intersectsBvh_overlapping() {
        var first = new BVH(List.of(box(0, 5), box(6, 10)));
        var second = new BVH(List.of(box(4, 8)));

        assertIntersection(first, second);
    }

    @Test
    void intersectsBvh_nonOverlapping() {
        var first = new BVH(List.of(box(0, 5), box(6, 10)));
        var second = new BVH(List.of(box(11, 15)));

        assertNoIntersection(first, second);
    }

    @Test
    void intersectsBvh_minimal() {
        var first = new BVH(List.of(box(0, 5)));
        var second = new BVH(List.of(box(5, 10)));

        assertIntersection(first, second);
    }

    @Test
    void intersectsBvh_touching() {
        var first = new BVH(List.of(box(0, 5)));
        var second = new BVH(List.of(box(6, 10)));

        assertNoIntersection(first, second);
    }

    @Test
    void intersectsBvh_complex() {
        var first = new BVH(List.of(box(0, 2), box(3, 5), box(6, 8), box(4, 7)));
        var second = new BVH(List.of(box(4, 4), box(9, 10), box(11, 17)));

        assertIntersection(first, second);
    }

    @Test
    void intersectsBvh_nested() {
        var first = new BVH(List.of(box(0, 10)));
        var second = new BVH(List.of(box(2, 5)));

        assertIntersection(first, second);
    }

    @Test
    void intersectsBvh_same() {
        var first = new BVH(List.of(box(0, 5)));
        var second = new BVH(List.of(box(0, 5)));

        assertIntersection(first, second);
    }

    @Test
    void intersectsBvh_disjoint() {
        var first = new BVH(List.of(box(0, 2), box(3, 5)));
        var second = new BVH(List.of(box(6, 8)));

        assertNoIntersection(first, second);
    }

    void assertIntersection(BVH first, BVH second) {
        assertTrue(first.intersects(second));
        assertTrue(second.intersects(first));
    }

    void assertNoIntersection(BVH first, BVH second) {
        assertFalse(first.intersects(second));
        assertFalse(second.intersects(first));
    }

    private static @NotNull BlockBox box(int i, int j) {
        return new BlockBox(new BlockPos(i, i, i), new BlockPos(j, j, j));
    }
}