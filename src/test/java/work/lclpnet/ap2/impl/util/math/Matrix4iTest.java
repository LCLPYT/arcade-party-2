package work.lclpnet.ap2.impl.util.math;

import net.minecraft.util.math.Vec3i;
import org.junit.jupiter.api.Test;
import work.lclpnet.kibu.util.math.Matrix3i;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Matrix4iTest {

    @Test
    void identity() {
        Matrix4i identity = new Matrix4i();
        assertEquals(new Vec3i(1, 2, 3), identity.transform(1, 2, 3));
        assertEquals(new Vec3i(-1, -2, -3), identity.transform(-1, -2, -3));
    }

    @Test
    void fromMatrix3() {
        Matrix3i mat3 = new Matrix3i(new int[] {
                1, 2, 3,
                4, 5, 6,
                7, 8, 9
        });

        assertEquals(new Matrix4i(new int[] {
                1, 2, 3, 0,
                4, 5, 6, 0,
                7, 8, 9, 0,
                0, 0, 0, 1
        }), new Matrix4i(mat3));
    }

    @Test
    void multiply() {
        Matrix4i rotation = new Matrix4i(Matrix3i.makeRotationY(1));
        Matrix4i translation = Matrix4i.makeTranslation(2, 2, 2);

        assertEquals(new Matrix4i(new int[] {
            0, 0, 1, 2,
            0, 1, 0, 2,
            -1, 0, 0, -2,
            0, 0, 0, 1
        }), rotation.multiply(translation));
    }

    @Test
    void multiplySample() {
        int[] elements = {
                1, 2, 3, 4,
                5, 6, 7, 8,
                9, 10, 11, 12,
                13, 14, 15, 16
        };

        assertEquals(new Matrix4i(new int[] {
                90, 100, 110, 120,
                202, 228, 254, 280,
                314, 356, 398, 440,
                426, 484, 542, 600
        }), new Matrix4i(elements).multiply(new Matrix4i(elements)));
    }

    @Test
    void translate() {
        Matrix4i rotation = new Matrix4i(Matrix3i.makeRotationY(1));
        Matrix4i composed = rotation.translate(2, 2, 2);

        assertEquals(new Matrix4i(new int[] {
                0, 0, 1, 2,
                0, 1, 0, 2,
                -1, 0, 0, 2,
                0, 0, 0, 1
        }), composed);
    }

    @Test
    void transform() {
        Matrix4i mat = new Matrix4i(new int[] {
                0, 0, 1, 2,
                0, 1, 0, 2,
                -1, 0, 0, 2,
                0, 0, 0, 1
        });

        assertEquals(new Vec3i(3, 2, 1), mat.transform(1, 0, 1));
    }
}