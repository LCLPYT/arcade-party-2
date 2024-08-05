package work.lclpnet.ap2.impl.util;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class QuadTreeTest {

    @Nested
    class Identity {
        private QuadTree<Vec3d> quadTree;

        @BeforeEach
        void setUp() {
            quadTree = new QuadTree<>(0.0, 0.0, 100.0, 100.0, 4, Function.identity());
        }

        @Test
        void testInitial() {
            assertNull(quadTree.root.entries);
            assertFalse(quadTree.root.divided);
            assertNull(quadTree.root.nw);
            assertNull(quadTree.root.ne);
            assertNull(quadTree.root.sw);
            assertNull(quadTree.root.se);
        }

        @Test
        void testCountInitial() {
            assertEquals(0, quadTree.root.count());
        }

        @Test
        void testAddWithinBounds() {
            Vec3d point = new Vec3d(10, 0, 10);
            assertTrue(quadTree.add(point));
            assertEquals(1, quadTree.root.count());
        }

        @Test
        void testAddOutOfBounds() {
            Vec3d point = new Vec3d(-10, 0, -10);
            assertFalse(quadTree.add(point));
            assertEquals(0, quadTree.root.count());
        }

        @Test
        void testQuadTreeSplitsCorrectly() {
            Vec3d point1 = new Vec3d(10, 0, 10);
            Vec3d point2 = new Vec3d(15, 0, 15);
            Vec3d point3 = new Vec3d(20, 0, 20);
            Vec3d point4 = new Vec3d(25, 0, 25);
            Vec3d point5 = new Vec3d(30, 0, 30);

            assertTrue(quadTree.add(point1));
            assertTrue(quadTree.add(point2));
            assertTrue(quadTree.add(point3));
            assertTrue(quadTree.add(point4));

            // Insert a fifth point, which should cause the root node to split
            assertTrue(quadTree.add(point5));

            assertTrue(quadTree.root.divided);
            assertNull(quadTree.root.entries);
            assertNotNull(quadTree.root.nw);
            assertNotNull(quadTree.root.ne);
            assertNotNull(quadTree.root.sw);
            assertNotNull(quadTree.root.se);

            assertTrue(quadTree.root.nw.divided);  // the 5 points are in root.nw, therefore it should have split again
            assertFalse(quadTree.root.ne.divided);
            assertFalse(quadTree.root.sw.divided);
            assertFalse(quadTree.root.se.divided);

            assertNull(quadTree.root.nw.entries);
            assertNull(quadTree.root.ne.entries);
            assertNull(quadTree.root.sw.entries);
            assertNull(quadTree.root.se.entries);

            assertNotNull(quadTree.root.nw.nw);
            assertNotNull(quadTree.root.nw.ne);
            assertNotNull(quadTree.root.nw.sw);
            assertNotNull(quadTree.root.nw.se);

            assertNotNull(quadTree.root.nw.nw.entries);
            assertNull(quadTree.root.nw.ne.entries);
            assertNull(quadTree.root.nw.sw.entries);
            assertNotNull(quadTree.root.nw.se.entries);

            assertEquals(3, quadTree.root.nw.nw.entries.size());
            assertEquals(2, quadTree.root.nw.se.entries.size());
        }

        @Test
        void testCapacityNotExceeded() {
            Vec3d point1 = new Vec3d(10, 0, 10);
            Vec3d point2 = new Vec3d(15, 0, 15);
            Vec3d point3 = new Vec3d(20, 0, 20);
            Vec3d point4 = new Vec3d(25, 0, 25);

            // Insert points up to the node capacity
            assertTrue(quadTree.add(point1));
            assertTrue(quadTree.add(point2));
            assertTrue(quadTree.add(point3));
            assertTrue(quadTree.add(point4));

            // QuadTree should still hold all points at this stage
            assertFalse(quadTree.root.divided);
            assertNotNull(quadTree.root.entries);
            assertEquals(4, quadTree.root.entries.size());
            assertNull(quadTree.root.nw);
            assertNull(quadTree.root.ne);
            assertNull(quadTree.root.sw);
            assertNull(quadTree.root.se);
        }

        @Test
        void testInsertionAfterSplit() {
            Vec3d point1 = new Vec3d(10, 0, 10);
            Vec3d point2 = new Vec3d(90, 0, 90);
            Vec3d point3 = new Vec3d(10, 0, 90);
            Vec3d point4 = new Vec3d(90, 0, 10);
            Vec3d point5 = new Vec3d(50, 0, 50);

            // Insert points
            quadTree.add(point1);
            quadTree.add(point2);
            quadTree.add(point3);
            quadTree.add(point4);

            // Insert another point which should cause a split
            quadTree.add(point5);

            // Ensure that a point after splitting is inserted correctly
            Vec3d newPoint = new Vec3d(55, 0, 55);
            assertTrue(quadTree.add(newPoint));
        }

        @Test
        void testAddBoundaryConditions() {
            Vec3d pointOnBoundary1 = new Vec3d(100, 0, 100);
            Vec3d pointOnBoundary2 = new Vec3d(0, 0, 0);

            // Test boundary conditions
            assertFalse(quadTree.add(pointOnBoundary1));
            assertTrue(quadTree.add(pointOnBoundary2));
        }

        @Test
        void testRemoveElement() {
            Vec3d point = new Vec3d(10, 0, 10);
            quadTree.add(point);
            assertEquals(1, quadTree.root.count());

            assertTrue(quadTree.remove(point));
            assertEquals(0, quadTree.root.count());
        }

        @Test
        void testRemoveElementThatDoesNotExist() {
            Vec3d point = new Vec3d(10, 0, 10);
            quadTree.add(point);
            assertEquals(1, quadTree.root.count());

            Vec3d nonExistentPoint = new Vec3d(20, 0, 20);
            assertFalse(quadTree.remove(nonExistentPoint));
            assertEquals(1, quadTree.root.count());
        }

        @Test
        void testRemoveElementAndMerge() {
            Vec3d point1 = new Vec3d(10, 0, 10);
            Vec3d point2 = new Vec3d(90, 0, 90);
            Vec3d point3 = new Vec3d(10, 0, 90);
            Vec3d point4 = new Vec3d(90, 0, 10);
            Vec3d point5 = new Vec3d(50, 0, 50);

            quadTree.add(point1);
            quadTree.add(point2);
            quadTree.add(point3);
            quadTree.add(point4);
            quadTree.add(point5);

            // At this point, the root node should have split
            assertTrue(quadTree.root.divided);

            // Remove an element that causes a merge
            assertTrue(quadTree.remove(point5));
            assertTrue(quadTree.remove(point4));
            assertTrue(quadTree.remove(point3));
            assertTrue(quadTree.remove(point2));

            // After removing point2, point3, point4, and point5, the tree should merge back to a single node
            assertFalse(quadTree.root.divided);
            assertEquals(1, quadTree.root.count());

            // Finally, remove the last element
            assertTrue(quadTree.remove(point1));
            assertEquals(0, quadTree.root.count());
            assertNull(quadTree.root.entries);
        }

        @Test
        void testRemoveBoundaryConditions() {
            Vec3d pointOnBoundary1 = new Vec3d(100, 0, 100);
            Vec3d pointOnBoundary2 = new Vec3d(0, 0, 0);

            // Boundary points, one is within and one is out of bounds
            assertFalse(quadTree.add(pointOnBoundary1));
            assertEquals(0, quadTree.root.count());

            assertTrue(quadTree.add(pointOnBoundary2));
            assertEquals(1, quadTree.root.count());

            assertTrue(quadTree.remove(pointOnBoundary2));
            assertEquals(0, quadTree.root.count());
        }

        @Test
        void testChildrenIterator() {
            Vec3d point1 = new Vec3d(10, 0, 10);
            Vec3d point2 = new Vec3d(90, 0, 90);
            Vec3d point3 = new Vec3d(10, 0, 90);
            Vec3d point4 = new Vec3d(90, 0, 10);
            Vec3d point5 = new Vec3d(50, 0, 50);

            quadTree.add(point1);
            quadTree.add(point2);
            quadTree.add(point3);
            quadTree.add(point4);
            quadTree.add(point5);

            var root = quadTree.getRoot();
            assertTrue(root.divided());

            var it = root.children();
            int i = 0;

            while (it.hasNext()) {
                it.next();
                i++;
            }

            assertEquals(4, i);
        }
    }

    @Test
    void testExceptionForInvalidNodeCapacity() {
        assertThrows(IllegalArgumentException.class,
                () -> new QuadTree<>(0, 0, 100, 100, 0, Function.identity()));
    }

    @Test
    void testExceptionForInvalidWidth() {
        assertThrows(IllegalArgumentException.class,
                () -> new QuadTree<>(0, 0, -5, 100, 3, Function.identity()));
    }

    @Test
    void testExceptionForInvalidLength() {
        assertThrows(IllegalArgumentException.class,
                () -> new QuadTree<>(0, 0, 100, -1, 1, Function.identity()));
    }

    @Test
    void testUpdateNotAddedNoop() {
        var tree = new QuadTree<MutPos>(0, 0, 100, 100, 2, mutPos -> new Vec3d(mutPos.x, 0, mutPos.z));
        var mutPos = new MutPos(10, 20);

        tree.update(mutPos);

        assertFalse(tree.root.divided);
        assertEquals(0, tree.root.count());
    }

    @Test
    void testUpdateNotSplitNoUpdate() {
        var tree = new QuadTree<MutPos>(0, 0, 100, 100, 2, mutPos -> new Vec3d(mutPos.x, 0, mutPos.z));
        var mutPos = new MutPos(10, 20);

        tree.add(mutPos);

        assertFalse(tree.root.divided);
        assertEquals(1, tree.root.count());

        mutPos.x = 60;
        mutPos.z = 5;
        tree.update(mutPos);

        assertFalse(tree.root.divided);
        assertEquals(1, tree.root.count());
        assertNotNull(tree.root.entries);

        // verify tracked position was updated
        Vec3d pos = tree.root.entries.getFirst().pos;
        assertEquals(60.0, pos.x, 1e-9);
        assertEquals(5.0, pos.z, 1e-9);
    }

    @Test
    void testAddTwiceUpdate() {
        var tree = new QuadTree<MutPos>(0, 0, 100, 100, 2, mutPos -> new Vec3d(mutPos.x, 0, mutPos.z));
        var mutPos = new MutPos(10, 20);

        tree.add(mutPos);

        mutPos.x = 60;
        mutPos.z = 5;
        assertFalse(tree.add(mutPos));  // not added twice

        assertFalse(tree.root.divided);
        assertEquals(1, tree.root.count());
        assertNotNull(tree.root.entries);

        // verify tracked position was updated
        Vec3d pos = tree.root.entries.getFirst().pos;
        assertEquals(60.0, pos.x, 1e-9);
        assertEquals(5.0, pos.z, 1e-9);
    }

    @Test
    void testUpdateNodeChanged() {
        var tree = new QuadTree<MutPos>(0, 0, 100, 100, 2, mutPos -> new Vec3d(mutPos.x, 0, mutPos.z));
        var mutPos = new MutPos(10, 20);

        tree.add(mutPos);
        tree.add(new MutPos(2, 85));
        tree.add(new MutPos(43, 96));
        tree.add(new MutPos(54, 96));
        tree.add(new MutPos(72, 11));

        assertTrue(tree.root.divided);
        assertNotNull(tree.root.nw);
        assertNotNull(tree.root.ne);
        assertEquals(1, tree.root.nw.count());
        assertEquals(1, tree.root.ne.count());

        mutPos.x = 60;
        mutPos.z = 5;
        tree.update(mutPos);

        assertTrue(tree.root.divided);
        assertEquals(0, tree.root.nw.count());
        assertEquals(2, tree.root.ne.count());
    }

    @Test
    void testUpdateSameNodeUnchanged() {
        var tree = new QuadTree<MutPos>(0, 0, 100, 100, 2, mutPos -> new Vec3d(mutPos.x, 0, mutPos.z));
        var mutPos = new MutPos(10, 20);

        tree.add(mutPos);
        tree.add(new MutPos(2, 85));
        tree.add(new MutPos(43, 96));
        tree.add(new MutPos(54, 96));
        tree.add(new MutPos(72, 11));

        assertTrue(tree.root.divided);
        assertNotNull(tree.root.nw);
        assertEquals(1, tree.root.nw.count());

        mutPos.x = 49;
        mutPos.z = 31;
        tree.update(mutPos);

        assertTrue(tree.root.divided);
        assertEquals(1, tree.root.nw.count());
    }

    @Test
    void testUpdateComplex() {
        var tree = new QuadTree<MutPos>(0, 0, 100, 100, 4, mutPos -> new Vec3d(mutPos.x, 0, mutPos.z));
        var mutPos = new MutPos(10, 20);

        // add all to nw
        tree.add(mutPos);
        tree.add(new MutPos(2, 47));
        tree.add(new MutPos(10, 23));
        tree.add(new MutPos(20, 16));
        tree.add(new MutPos(30, 12));

        assertNotNull(tree.root.nw);
        assertTrue(tree.root.nw.divided);
        assertEquals(5, tree.root.nw.count());

        // move to ne
        mutPos.x = 60;
        mutPos.z = 30;
        tree.update(mutPos);

        assertFalse(tree.root.nw.divided);
        assertEquals(4, tree.root.nw.count());
        assertNotNull(tree.root.ne);
        assertEquals(1, tree.root.ne.count());
    }

    static class MutPos {
        double x, z;

        MutPos(double x, double z) {
            this.x = x;
            this.z = z;
        }
    }
}