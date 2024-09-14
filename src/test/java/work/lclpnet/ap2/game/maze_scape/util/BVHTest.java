package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.math.AffineIntMatrix;
import work.lclpnet.kibu.util.math.Matrix3i;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BVHTest {

    @Test
    void build_emptyList() {
        var bvh = BVH.build(List.of());

        assertSame(BVH.EMPTY, bvh);
    }

    @Test
    void intersectsBox_singleBox() {
        var bvh = BVH.build(List.of(box(0, 2)));

        assertTrue(bvh.intersects(box(1, 3)));
    }

    @Test
    void intersectsBox_noIntersection() {
        var bvh = BVH.build(List.of(box(0, 2), box(5, 7)));

        assertFalse(bvh.intersects(box(3, 4)));
    }

    @Test
    void intersectsBox_multipleBoxes() {
        var bvh = BVH.build(List.of(box(0, 2), box(3, 5)));

        assertTrue(bvh.intersects(box(1, 4)));
    }

    @Test
    void intersectsBox_edgeTouching() {
        var bvh = BVH.build(List.of(box(0, 2), box(3, 5)));

        assertTrue(bvh.intersects(box(2, 3)));
    }

    @Test
    void intersectsBox_containedBox() {
        BVH bvh = BVH.build(List.of(box(0, 5)));

        assertTrue(bvh.intersects(box(1, 2)));
    }

    @Test
    void intersectsBox_nestedBVH() {
        var bvh = BVH.build(List.of(box(0, 2), box(3, 5), box(6, 8)));

        assertTrue(bvh.intersects(box(4, 7)));
    }

    @Test
    void intersectsBvh_overlapping() {
        var first = BVH.build(List.of(box(0, 5), box(6, 10)));
        var second = BVH.build(List.of(box(4, 8)));

        assertIntersection(first, second);
    }

    @Test
    void intersectsBvh_nonOverlapping() {
        var first = BVH.build(List.of(box(0, 5), box(6, 10)));
        var second = BVH.build(List.of(box(11, 15)));

        assertNoIntersection(first, second);
    }

    @Test
    void intersectsBvh_minimal() {
        var first = BVH.build(List.of(box(0, 5)));
        var second = BVH.build(List.of(box(5, 10)));

        assertIntersection(first, second);
    }

    @Test
    void intersectsBvh_touching() {
        var first = BVH.build(List.of(box(0, 5)));
        var second = BVH.build(List.of(box(6, 10)));

        assertNoIntersection(first, second);
    }

    @Test
    void intersectsBvh_complex() {
        var first = BVH.build(List.of(box(0, 2), box(3, 5), box(6, 8), box(4, 7)));
        var second = BVH.build(List.of(box(4, 4), box(9, 10), box(11, 17)));

        assertIntersection(first, second);
    }

    @Test
    void intersectsBvh_nested() {
        var first = BVH.build(List.of(box(0, 10)));
        var second = BVH.build(List.of(box(2, 5)));

        assertIntersection(first, second);
    }

    @Test
    void intersectsBvh_same() {
        var first = BVH.build(List.of(box(0, 5)));
        var second = BVH.build(List.of(box(0, 5)));

        assertIntersection(first, second);
    }

    @Test
    void intersectsBvh_disjoint() {
        var first = BVH.build(List.of(box(0, 2), box(3, 5)));
        var second = BVH.build(List.of(box(6, 8)));

        assertNoIntersection(first, second);
    }

    @Test
    void transform_rotate() {
        var bvh = BVH.build(List.of(box(0, 3), box(-1, 2), box(2, 5)));
        bvh = bvh.transform(new AffineIntMatrix(Matrix3i.makeRotationY(2)));

        assertNotNull(bvh.root);
        assertNotNull(bvh.root.left);
        assertNotNull(bvh.root.right);
        assertNotNull(bvh.root.right.left);
        assertNotNull(bvh.root.right.right);

        assertEquals(new BlockBox(-5, -1, -5, 1, 5, 1), bvh.root.bounds);
        assertEquals(new BlockBox(-2, -1, -2, 1, 2, 1), bvh.root.left.bounds);
        assertEquals(new BlockBox(-5, 0, -5, 0, 5, 0), bvh.root.right.bounds);
        assertEquals(new BlockBox(-3, 0, -3, 0, 3, 0), bvh.root.right.left.bounds);
        assertEquals(new BlockBox(-5, 2, -5, -2, 5, -2), bvh.root.right.right.bounds);
    }

    @Test
    void transform_affine() {
        var bvh = BVH.build(List.of(box(0, 3), box(-1, 2), box(2, 5)));
        bvh = bvh.transform(new AffineIntMatrix(Matrix3i.makeRotationY(2), 1, 2, 3));

        assertNotNull(bvh.root);
        assertNotNull(bvh.root.left);
        assertNotNull(bvh.root.right);
        assertNotNull(bvh.root.right.left);
        assertNotNull(bvh.root.right.right);

        assertEquals(new BlockBox(-4, 1, -2, 2, 7, 4), bvh.root.bounds);
        assertEquals(new BlockBox(-1, 1, 1, 2, 4, 4), bvh.root.left.bounds);
        assertEquals(new BlockBox(-4, 2, -2, 1, 7, 3), bvh.root.right.bounds);
        assertEquals(new BlockBox(-2, 2, 0, 1, 5, 3), bvh.root.right.left.bounds);
        assertEquals(new BlockBox(-4, 4, -2, -1, 7, 1), bvh.root.right.right.bounds);
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