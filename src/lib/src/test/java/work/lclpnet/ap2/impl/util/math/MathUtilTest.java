package work.lclpnet.ap2.impl.util.math;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static work.lclpnet.ap2.impl.util.math.MathUtil.corners;

class MathUtilTest {

    @Test
    void corners_unitBox() {
        var box = new AABB(0, 0, 0, 1, 1, 1);

        assertIterableEquals(List.of(
                new Vec3(0, 0, 0),
                new Vec3(1, 0, 0),
                new Vec3(0, 0, 1),
                new Vec3(1, 0, 1),
                new Vec3(0, 1, 0),
                new Vec3(1, 1, 0),
                new Vec3(0, 1, 1),
                new Vec3(1, 1, 1)
        ), corners(box));
    }

    @Test
    void corners_negative() {
        AABB box = new AABB(-1, -1, -1, 0, 0, 0);

        assertIterableEquals(List.of(
                new Vec3(-1, -1, -1),
                new Vec3(0, -1, -1),
                new Vec3(-1, -1, 0),
                new Vec3(0, -1, 0),
                new Vec3(-1, 0, -1),
                new Vec3(0, 0, -1),
                new Vec3(-1, 0, 0),
                new Vec3(0, 0, 0)
        ), corners(box));
    }

    @Test
    void corners_sampleBox() {
        AABB box = new AABB(2, 3, 4, 5, 6, 7);

        assertIterableEquals(List.of(
                new Vec3(2, 3, 4),
                new Vec3(5, 3, 4),
                new Vec3(2, 3, 7),
                new Vec3(5, 3, 7),
                new Vec3(2, 6, 4),
                new Vec3(5, 6, 4),
                new Vec3(2, 6, 7),
                new Vec3(5, 6, 7)
        ), corners(box));
    }

    @Test
    void corners_flat() {
        AABB box = new AABB(0, 0, 0, 1, 0, 1);

        assertIterableEquals(List.of(
                new Vec3(0, 0, 0),
                new Vec3(1, 0, 0),
                new Vec3(0, 0, 1),
                new Vec3(1, 0, 1),
                new Vec3(0, 0, 0),
                new Vec3(1, 0, 0),
                new Vec3(0, 0, 1),
                new Vec3(1, 0, 1)
        ), corners(box));
    }

    @Test
    void corners_hasNext() {
        AABB box = new AABB(0, 0, 0, 1, 1, 1);
        var iterator = corners(box).iterator();

        for (int i = 0; i < 8; i++) {
            assertTrue(iterator.hasNext());
            iterator.next();
        }

        assertFalse(iterator.hasNext());
    }
}