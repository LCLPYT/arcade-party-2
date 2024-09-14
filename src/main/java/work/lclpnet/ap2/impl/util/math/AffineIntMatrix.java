package work.lclpnet.ap2.impl.util.math;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import work.lclpnet.kibu.util.math.Matrix3i;

import java.util.Arrays;

public class AffineIntMatrix {

    public final int[] elements;

    public AffineIntMatrix(int[] elements) {
        if (elements.length != 12) throw new IllegalArgumentException("Invalid elements size");
        this.elements = elements;
    }

    public AffineIntMatrix() {
        elements = new int[12];
        elements[1] = elements[2] = elements[3] = elements[4] = elements[6] = elements[7] = elements[8] = elements[9] = elements[11] = 0;
        elements[0] = elements[5] = elements[10] = 1;
    }

    public AffineIntMatrix(Matrix3i matrix3) {
        this(matrix3, 0, 0, 0);
    }

    public AffineIntMatrix(Matrix3i matrix3, Vec3i translation) {
        this(matrix3, translation.getX(), translation.getY(), translation.getZ());
    }

    public AffineIntMatrix(Matrix3i matrix3, int translateX, int translateY, int translateZ) {
        elements = new int[12];

        int[] me = matrix3.elements;

        elements[0] = me[0]; elements[1] = me[1]; elements[2] = me[2]; elements[3] = translateX;
        elements[4] = me[3]; elements[5] = me[4]; elements[6] = me[5]; elements[7] = translateY;
        elements[8] = me[6]; elements[9] = me[7]; elements[10] = me[8]; elements[11] = translateZ;
    }

    public void transform(int x, int y, int z, BlockPos.Mutable target) {
        // points have w=1 in homogeneous coordinates
        target.set(
                elements[0] * x + elements[1] * y + elements[2] * z + elements[3],
                elements[4] * x + elements[5] * y + elements[6] * z + elements[7],
                elements[8] * x + elements[9] * y + elements[10] * z + elements[11]
        );
    }

    public void transformVector(int x, int y, int z, BlockPos.Mutable target) {
        // vectors have w=0 in homogeneous coordinates
        target.set(
                elements[0] * x + elements[1] * y + elements[2] * z,
                elements[4] * x + elements[5] * y + elements[6] * z,
                elements[8] * x + elements[9] * y + elements[10] * z
        );
    }

    public BlockPos transform(int x, int y, int z) {
        BlockPos.Mutable pos = new BlockPos.Mutable();

        transform(x, y, z, pos);

        return pos;
    }

    public BlockPos transformVector(int x, int y, int z) {
        BlockPos.Mutable vec = new BlockPos.Mutable();

        transformVector(x, y, z, vec);

        return vec;
    }

    public Vec3d transformVector(double x, double y, double z) {
        // vectors have w=0 in homogeneous coordinates
        return new Vec3d(
                elements[0] * x + elements[1] * y + elements[2] * z,
                elements[4] * x + elements[5] * y + elements[6] * z,
                elements[8] * x + elements[9] * y + elements[10] * z
        );
    }

    public BlockPos transformVector(Vec3i vec) {
        return transformVector(vec.getX(), vec.getY(), vec.getZ());
    }

    public BlockPos transform(Vec3i pos) {
        return transform(pos.getX(), pos.getY(), pos.getZ());
    }

    public AffineIntMatrix multiply(AffineIntMatrix other) {
        AffineIntMatrix dest = new AffineIntMatrix();
        multiply(this, other, dest);
        return dest;
    }

    public AffineIntMatrix translate(Vec3i translation) {
        return translate(translation.getX(), translation.getY(), translation.getZ());
    }

    public AffineIntMatrix translate(int x, int y, int z) {
        return makeTranslation(x, y, z).multiply(this);
    }

    /**
     * Gets the linear part of the affine transformation (scale, rotate and shear) as 3x3 matrix.
     * @return The linear part of this transformation, without translation.
     */
    public Matrix3i linearPart() {
        return new Matrix3i(new int[] {
                elements[0], elements[1], elements[2],
                elements[4], elements[5], elements[6],
                elements[8], elements[9], elements[10]
        });
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AffineIntMatrix affineMatrix = (AffineIntMatrix) o;
        return Arrays.equals(elements, affineMatrix.elements);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(elements);
    }

    @Override
    public String toString() {
        return "(%s %s %s %s\n%s %s %s %s\n%s %s %s %s)".formatted(
                elements[0], elements[1], elements[2], elements[3],
                elements[4], elements[5], elements[6], elements[7],
                elements[8], elements[9], elements[10], elements[11]
        );
    }

    public static void multiply(AffineIntMatrix left, AffineIntMatrix right, AffineIntMatrix dest) {
        // 4th-row is implicitly [0, 0, 0, 1]
        for (int row = 0; row < 3; row++) {
            int rowOffset = row * 4;

            for (int col = 0; col < 4; col++) {
                dest.elements[rowOffset + col] = left.elements[rowOffset] * right.elements[col] +
                        left.elements[rowOffset + 1] * right.elements[4 + col] +
                        left.elements[rowOffset + 2] * right.elements[8 + col];

                if (col == 3) {
                    dest.elements[rowOffset + col] += left.elements[rowOffset + 3];
                }
            }
        }
    }

    public static AffineIntMatrix makeTranslation(int x, int y, int z) {
        return new AffineIntMatrix(new int[] {
                1, 0, 0, x,
                0, 1, 0, y,
                0, 0, 1, z
        });
    }
}
