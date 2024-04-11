package work.lclpnet.ap2.impl.util;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class BlockBoxTest {

    @Test
    public void testCollidesWith_intersectingBoxes_returnsTrue() {
        BlockBox box1 = new BlockBox(0, 0, 0, 5, 5, 5);
        BlockBox box2 = new BlockBox(3, 3, 3, 8, 8, 8);

        boolean result = box1.collidesWith(box2);

        assertTrue(result);
    }

    @Test
    public void testCollidesWith_nonIntersectingBoxes_returnsFalse() {
        BlockBox box1 = new BlockBox(0, 0, 0, 5, 5, 5);
        BlockBox box2 = new BlockBox(6, 6, 6, 10, 10, 10);

        boolean result = box1.collidesWith(box2);

        assertFalse(result);
    }

    @Test
    public void testCollidesWith_equalBoxes_returnsTrue() {
        BlockBox box1 = new BlockBox(0, 0, 0, 5, 5, 5);
        BlockBox box2 = new BlockBox(0, 0, 0, 5, 5, 5);

        boolean result = box1.collidesWith(box2);

        assertTrue(result);
    }

    @Test
    public void testCollidesWith_partialOverlap_returnsTrue() {
        BlockBox box1 = new BlockBox(0, 0, 0, 5, 5, 5);
        BlockBox box2 = new BlockBox(3, 3, 3, 8, 8, 8);

        boolean result = box1.collidesWith(box2);

        assertTrue(result);
    }

    @Test
    public void testCollidesWith_oneBoxInsideAnother_returnsTrue() {
        BlockBox box1 = new BlockBox(0, 0, 0, 10, 10, 10);
        BlockBox box2 = new BlockBox(3, 3, 3, 8, 8, 8);

        boolean result = box1.collidesWith(box2);

        assertTrue(result);
    }

    @Test
    public void getClosestPoint_contained_self() {
        BlockBox box = new BlockBox(0, 0, 0, 2, 2, 2);
        assertEquals(new Vec3d(1, 1, 1), box.getClosestPoint(new Vec3d(1, 1, 1)));
    }

    @ParameterizedTest
    @CsvSource({
            "3,1,1,2,1,1", "1,3,1,1,2,1", "1,1,3,1,1,2", "-1,1,1,0,1,1", "1,-1,1,1,0,1", "1,1,-1,1,1,0",
            "1,-2,-3,1,0,0", "-5,1,-9,0,1,0", "-1,-2,1,0,0,1", "1,5,7,1,2,2", "4,1,3,2,1,2", "5,3,1,2,2,1",
            "3,3,3,2,2,2", "-1,-1,-1,0,0,0,0", "3,0,0,2,0,0", "0,4,0,0,2,0", "0,2,10,0,2,2", "0,-10,10,0,0,2"
    })
    public void getClosestPoint_outside_asExpected(int px, int py, int pz, int ex, int ey, int ez) {
        BlockBox box = new BlockBox(0, 0, 0, 2, 2, 2);
        Vec3d point = new Vec3d(px, py, pz);
        assertFalse(box.contains(point));

        Vec3d closestPoint = box.getClosestPoint(point);
        Vec3d expected = new Vec3d(ex, ey, ez);
        assertEquals(expected, closestPoint);
    }
}