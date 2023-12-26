package work.lclpnet.ap2.impl.util.math;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.Arrays;

public class Matrix3i {

    public final int[] elements;

    public Matrix3i(int[] elements) {
        if (elements.length != 9) throw new IllegalStateException("Invalid elements size");
        this.elements = elements;
    }

    public Matrix3i() {
        elements = new int[9];
        elements[1] = elements[2] = elements[3] = elements[5] = elements[6] = elements[7] = 0;
        elements[0] = elements[4] = elements[8] = 1;
    }

    public void transform(int x, int y, int z, BlockPos.Mutable target) {
        target.set(
                elements[0] * x + elements[1] * y + elements[2] * z,
                elements[3] * x + elements[4] * y + elements[5] * z,
                elements[6] * x + elements[7] * y + elements[8] * z
        );
    }

    public BlockPos transform(int x, int y, int z) {
        BlockPos.Mutable vec = new BlockPos.Mutable();

        transform(x, y, z, vec);

        return vec;
    }

    public BlockPos transform(Vec3i pos) {
        return transform(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Matrix3i matrix3i = (Matrix3i) o;
        return Arrays.equals(elements, matrix3i.elements);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(elements);
    }

    @Override
    public String toString() {
        return "(%s %s %s\n%s %s %s\n%s %s %s)".formatted(
                elements[0], elements[1], elements[2],
                elements[3], elements[4], elements[5],
                elements[6], elements[7], elements[8]
        );
    }

    public static Matrix3i makeRotationY(int rotation) {
        int sin = (int) Math.round(Math.sin(rotation * Math.PI * 0.5));
        int cos = (int) Math.round(Math.cos(rotation * Math.PI * 0.5));

        return new Matrix3i(new int[] {
                cos, 0, sin,
                0, 1, 0,
                -sin, 0, cos
        });
    }
}
