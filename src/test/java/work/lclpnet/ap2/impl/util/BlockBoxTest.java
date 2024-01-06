package work.lclpnet.ap2.impl.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}