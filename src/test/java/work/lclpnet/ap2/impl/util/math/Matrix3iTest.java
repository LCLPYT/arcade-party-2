package work.lclpnet.ap2.impl.util.math;

import net.minecraft.util.math.Vec3i;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Matrix3iTest {

    @Test
    void identity() {
        Matrix3i identity = new Matrix3i();
        assertEquals(new Vec3i(1, 2, 3), identity.transform(1, 2, 3));
        assertEquals(new Vec3i(-1, -2, -3), identity.transform(-1, -2, -3));
    }

    @Test
    void testRotateYBy0() {
        Matrix3i rotation = Matrix3i.makeRotationY(0);
        assertEquals(new Matrix3i(), rotation);
    }

    @Test
    void testRotateYBy90() {
        Matrix3i rotation = Matrix3i.makeRotationY(1);
        assertEquals(new Vec3i(1, 0, -1), rotation.transform(1, 0, 1));
        assertEquals(new Vec3i(1, 0, 1), rotation.transform(-1, 0, 1));
        assertEquals(new Vec3i(-1, 0, -1), rotation.transform(1, 0, -1));
        assertEquals(new Vec3i(-1, 0, 1), rotation.transform(-1, 0, -1));
    }

    @Test
    void testRotateYBy180() {
        Matrix3i rotation = Matrix3i.makeRotationY(2);
        assertEquals(new Vec3i(-1, 0, -1), rotation.transform(1, 0, 1));
        assertEquals(new Vec3i(1, 0, -1), rotation.transform(-1, 0, 1));
        assertEquals(new Vec3i(-1, 0, 1), rotation.transform(1, 0, -1));
        assertEquals(new Vec3i(1, 0, 1), rotation.transform(-1, 0, -1));
    }

    @Test
    void testRotateYBy270() {
        Matrix3i rotation = Matrix3i.makeRotationY(3);
        assertEquals(new Vec3i(-1, 0, 1), rotation.transform(1, 0, 1));
        assertEquals(new Vec3i(-1, 0, -1), rotation.transform(-1, 0, 1));
        assertEquals(new Vec3i(1, 0, 1), rotation.transform(1, 0, -1));
        assertEquals(new Vec3i(1, 0, -1), rotation.transform(-1, 0, -1));
    }

    @Test
    void testRotateYBy360() {
        Matrix3i rotation = Matrix3i.makeRotationY(4);
        assertEquals(new Matrix3i(), rotation);
    }
}