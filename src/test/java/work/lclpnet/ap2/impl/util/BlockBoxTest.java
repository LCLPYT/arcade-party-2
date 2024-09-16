package work.lclpnet.ap2.impl.util;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class BlockBoxTest {

    @Test
    public void testIntersects_intersectingBoxes_returnsTrue() {
        BlockBox box1 = new BlockBox(0, 0, 0, 5, 5, 5);
        BlockBox box2 = new BlockBox(3, 3, 3, 8, 8, 8);

        boolean result = box1.intersects(box2);

        assertTrue(result);
    }

    @Test
    public void testIntersects_nonIntersectingBoxes_returnsFalse() {
        BlockBox box1 = new BlockBox(0, 0, 0, 5, 5, 5);
        BlockBox box2 = new BlockBox(6, 6, 6, 10, 10, 10);

        boolean result = box1.intersects(box2);

        assertFalse(result);
    }

    @Test
    public void testIntersects_equalBoxes_returnsTrue() {
        BlockBox box1 = new BlockBox(0, 0, 0, 5, 5, 5);
        BlockBox box2 = new BlockBox(0, 0, 0, 5, 5, 5);

        boolean result = box1.intersects(box2);

        assertTrue(result);
    }

    @Test
    public void testIntersects_partialOverlap_returnsTrue() {
        BlockBox box1 = new BlockBox(0, 0, 0, 5, 5, 5);
        BlockBox box2 = new BlockBox(3, 3, 3, 8, 8, 8);

        boolean result = box1.intersects(box2);

        assertTrue(result);
    }

    @Test
    public void testIntersects_oneBoxInsideAnother_returnsTrue() {
        BlockBox box1 = new BlockBox(0, 0, 0, 10, 10, 10);
        BlockBox box2 = new BlockBox(3, 3, 3, 8, 8, 8);

        boolean result = box1.intersects(box2);

        assertTrue(result);
    }

    @Test
    public void closestPoint_contained_self() {
        BlockBox box = new BlockBox(0, 0, 0, 2, 2, 2);
        assertEquals(new Vec3d(1, 1, 1), box.closestPoint(new Vec3d(1, 1, 1)));
    }

    @ParameterizedTest
    @CsvSource({
            "3,1,1,2,1,1", "1,3,1,1,2,1", "1,1,3,1,1,2", "-1,1,1,0,1,1", "1,-1,1,1,0,1", "1,1,-1,1,1,0",
            "1,-2,-3,1,0,0", "-5,1,-9,0,1,0", "-1,-2,1,0,0,1", "1,5,7,1,2,2", "4,1,3,2,1,2", "5,3,1,2,2,1",
            "3,3,3,2,2,2", "-1,-1,-1,0,0,0,0", "3,0,0,2,0,0", "0,4,0,0,2,0", "0,2,10,0,2,2", "0,-10,10,0,0,2"
    })
    public void closestPoint_outside_asExpected(int px, int py, int pz, int ex, int ey, int ez) {
        BlockBox box = new BlockBox(0, 0, 0, 2, 2, 2);
        Vec3d point = new Vec3d(px, py, pz);
        assertFalse(box.contains(point));

        Vec3d closestPoint = box.closestPoint(point);
        Vec3d expected = new Vec3d(ex, ey, ez);
        assertEquals(expected, closestPoint);
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0,2,2,2,27", "-1,-2,-1,2,2,2,80", "0,0,0,0,0,0,1", "0,0,0,-1,0,0,2", "-1,0,1,8,9,10,1000"
    })
    public void volume_givenBox_isCorrect(int x1, int y1, int z1, int x2, int y2, int z2, int expectedVolume) {
        BlockBox box = new BlockBox(x1, y1, z1, x2, y2, z2);

        assertEquals(expectedVolume, box.volume());
    }

    @Test
    public void indexToPosYZX_2x2x2_asExpected() {
        BlockBox box = new BlockBox(0, 0, 0, 1, 1, 1);

        assertEquals(new BlockPos(0, 0, 0), box.indexToPosYZX(0));
        assertEquals(new BlockPos(1, 0, 0), box.indexToPosYZX(1));
        assertEquals(new BlockPos(0, 0, 1), box.indexToPosYZX(2));
        assertEquals(new BlockPos(1, 0, 1), box.indexToPosYZX(3));
        assertEquals(new BlockPos(0, 1, 0), box.indexToPosYZX(4));
        assertEquals(new BlockPos(1, 1, 0), box.indexToPosYZX(5));
        assertEquals(new BlockPos(0, 1, 1), box.indexToPosYZX(6));
        assertEquals(new BlockPos(1, 1, 1), box.indexToPosYZX(7));
    }

    @Test
    public void indexToPosYZX_3x3x1_asExpected() {
        BlockBox box = new BlockBox(0, 0, 0, 2, 2, 0);

        assertEquals(new BlockPos(0, 0, 0), box.indexToPosYZX(0));
        assertEquals(new BlockPos(1, 0, 0), box.indexToPosYZX(1));
        assertEquals(new BlockPos(2, 0, 0), box.indexToPosYZX(2));
        assertEquals(new BlockPos(0, 1, 0), box.indexToPosYZX(3));
        assertEquals(new BlockPos(1, 1, 0), box.indexToPosYZX(4));
        assertEquals(new BlockPos(2, 1, 0), box.indexToPosYZX(5));
        assertEquals(new BlockPos(0, 2, 0), box.indexToPosYZX(6));
        assertEquals(new BlockPos(1, 2, 0), box.indexToPosYZX(7));
        assertEquals(new BlockPos(2, 2, 0), box.indexToPosYZX(8));
    }

    @Test
    public void indexToPosYZX_1x3x3_asExpected() {
        BlockBox box = new BlockBox(0, 0, 0, 0, 2, 2);

        assertEquals(new BlockPos(0, 0, 0), box.indexToPosYZX(0));
        assertEquals(new BlockPos(0, 0, 1), box.indexToPosYZX(1));
        assertEquals(new BlockPos(0, 0, 2), box.indexToPosYZX(2));
        assertEquals(new BlockPos(0, 1, 0), box.indexToPosYZX(3));
        assertEquals(new BlockPos(0, 1, 1), box.indexToPosYZX(4));
        assertEquals(new BlockPos(0, 1, 2), box.indexToPosYZX(5));
        assertEquals(new BlockPos(0, 2, 0), box.indexToPosYZX(6));
        assertEquals(new BlockPos(0, 2, 1), box.indexToPosYZX(7));
        assertEquals(new BlockPos(0, 2, 2), box.indexToPosYZX(8));
    }

    @Test
    public void posToIndexYZX_2x2x2_asExpected() {
        BlockBox box = new BlockBox(5, 5, 5, 6, 6, 6);

        assertEquals(0, box.posToIndexYZX(new BlockPos(5, 5, 5)));
        assertEquals(1, box.posToIndexYZX(new BlockPos(6, 5, 5)));
        assertEquals(2, box.posToIndexYZX(new BlockPos(5, 5, 6)));
        assertEquals(3, box.posToIndexYZX(new BlockPos(6, 5, 6)));
        assertEquals(4, box.posToIndexYZX(new BlockPos(5, 6, 5)));
        assertEquals(5, box.posToIndexYZX(new BlockPos(6, 6, 5)));
        assertEquals(6, box.posToIndexYZX(new BlockPos(5, 6, 6)));
        assertEquals(7, box.posToIndexYZX(new BlockPos(6, 6, 6)));
    }

    @Test
    public void posToIndexYZX_3x3x1_asExpected() {
        BlockBox box = new BlockBox(0, 0, 0, 2, 2, 0);

        assertEquals(0, box.posToIndexYZX(new BlockPos(0, 0, 0)));
        assertEquals(1, box.posToIndexYZX(new BlockPos(1, 0, 0)));
        assertEquals(2, box.posToIndexYZX(new BlockPos(2, 0, 0)));
        assertEquals(3, box.posToIndexYZX(new BlockPos(0, 1, 0)));
        assertEquals(4, box.posToIndexYZX(new BlockPos(1, 1, 0)));
        assertEquals(5, box.posToIndexYZX(new BlockPos(2, 1, 0)));
        assertEquals(6, box.posToIndexYZX(new BlockPos(0, 2, 0)));
        assertEquals(7, box.posToIndexYZX(new BlockPos(1, 2, 0)));
        assertEquals(8, box.posToIndexYZX(new BlockPos(2, 2, 0)));
    }

    @Test
    public void posToIndexYZX_1x3x3_asExpected() {
        BlockBox box = new BlockBox(0, 0, 0, 0, 2, 2);

        assertEquals(0, box.posToIndexYZX(new BlockPos(0, 0, 0)));
        assertEquals(1, box.posToIndexYZX(new BlockPos(0, 0, 1)));
        assertEquals(2, box.posToIndexYZX(new BlockPos(0, 0, 2)));
        assertEquals(3, box.posToIndexYZX(new BlockPos(0, 1, 0)));
        assertEquals(4, box.posToIndexYZX(new BlockPos(0, 1, 1)));
        assertEquals(5, box.posToIndexYZX(new BlockPos(0, 1, 2)));
        assertEquals(6, box.posToIndexYZX(new BlockPos(0, 2, 0)));
        assertEquals(7, box.posToIndexYZX(new BlockPos(0, 2, 1)));
        assertEquals(8, box.posToIndexYZX(new BlockPos(0, 2, 2)));
    }

    @Test
    void contains_fullyContained() {
        var first = new BlockBox(new BlockPos(0, 0, 0), new BlockPos(5, 5, 5));
        var second = new BlockBox(new BlockPos(1, 1, 1), new BlockPos(4, 4, 4));

        assertTrue(first.contains(second));
    }

    @Test
    void contains_notContained() {
        var first = new BlockBox(new BlockPos(0, 0, 0), new BlockPos(5, 5, 5));
        var second = new BlockBox(new BlockPos(1, 1, 1), new BlockPos(4, 4, 4));

        assertFalse(second.contains(first));
    }

    @Test
    void contains_partiallyOverlapping() {
        var first = new BlockBox(new BlockPos(0, 0, 0), new BlockPos(5, 5, 5));
        var second = new BlockBox(new BlockPos(3, 3, 3), new BlockPos(7, 7, 7));

        assertFalse(first.contains(second));
        assertFalse(second.contains(first));
    }

    @Test
    void contains_self() {
        var first = new BlockBox(new BlockPos(0, 0, 0), new BlockPos(5, 5, 5));

        assertTrue(first.contains(first));
    }

    @Test
    void contains_containedEdge() {
        var first = new BlockBox(new BlockPos(0, 0, 0), new BlockPos(5, 5, 5));
        var second = new BlockBox(new BlockPos(5, 5, 5), new BlockPos(5, 5, 5));

        assertTrue(first.contains(second));
        assertFalse(second.contains(first));
    }

    @Test
    void contains_edgeIntersect() {
        var first = new BlockBox(new BlockPos(0, 0, 0), new BlockPos(5, 5, 5));
        var second = new BlockBox(new BlockPos(5, 5, 5), new BlockPos(6, 6, 6));

        assertFalse(first.contains(second));
        assertFalse(second.contains(first));
    }

    @Test
    void contains_disjoint() {
        var first = new BlockBox(new BlockPos(0, 0, 0), new BlockPos(5, 5, 5));
        var second = new BlockBox(new BlockPos(6, 6, 6), new BlockPos(10, 10, 10));

        assertFalse(first.contains(second));
        assertFalse(second.contains(first));
    }

    @Test
    void contains_negativeCoordinates() {
        var first = new BlockBox(new BlockPos(0, 0, 0), new BlockPos(5, 5, 5));
        var second = new BlockBox(new BlockPos(-5, -5, -5), new BlockPos(-1, -1, -1));

        assertFalse(first.contains(second));
        assertFalse(second.contains(first));
    }

    @Test
    void contains_negativeContained() {
        var first = new BlockBox(new BlockPos(-5, -5, -5), new BlockPos(5, 5, 5));
        var second = new BlockBox(new BlockPos(-2, -2, -2), new BlockPos(2, 2, 2));

        assertTrue(first.contains(second));
        assertFalse(second.contains(first));
    }
}