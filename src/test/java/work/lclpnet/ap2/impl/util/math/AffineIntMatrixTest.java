package work.lclpnet.ap2.impl.util.math;

import net.minecraft.util.math.Vec3i;
import org.junit.jupiter.api.Test;
import work.lclpnet.kibu.util.math.Matrix3i;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AffineIntMatrixTest {

    @Test
    void identity() {
        AffineIntMatrix identity = new AffineIntMatrix();
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

        assertEquals(new AffineIntMatrix(new int[] {
                1, 2, 3, 0,
                4, 5, 6, 0,
                7, 8, 9, 0
        }), new AffineIntMatrix(mat3));
    }

    @Test
    void multiply() {
        AffineIntMatrix rotation = new AffineIntMatrix(Matrix3i.makeRotationY(1));
        AffineIntMatrix translation = AffineIntMatrix.makeTranslation(2, 2, 2);

        assertEquals(new AffineIntMatrix(new int[] {
            0, 0, 1, 2,
            0, 1, 0, 2,
            -1, 0, 0, -2
        }), rotation.multiply(translation));
    }

    @Test
    void multiplySample() {
        int[] elements = {
                1, 2, 3, 4,
                5, 6, 7, 8,
                9, 10, 11, 12
        };

        assertEquals(new AffineIntMatrix(new int[] {
                38, 44, 50, 60,
                98, 116, 134, 160,
                158, 188, 218, 260
        }), new AffineIntMatrix(elements).multiply(new AffineIntMatrix(elements)));
    }

    @Test
    void translate() {
        AffineIntMatrix rotation = new AffineIntMatrix(Matrix3i.makeRotationY(1));
        AffineIntMatrix composed = rotation.translate(2, 2, 2);

        assertEquals(new AffineIntMatrix(new int[] {
                0, 0, 1, 2,
                0, 1, 0, 2,
                -1, 0, 0, 2
        }), composed);
    }

    @Test
    void transform() {
        AffineIntMatrix mat = new AffineIntMatrix(new int[] {
                0, 0, 1, 2,
                0, 1, 0, 2,
                -1, 0, 0, 2
        });

        assertEquals(new Vec3i(3, 2, 1), mat.transform(1, 0, 1));
    }
}