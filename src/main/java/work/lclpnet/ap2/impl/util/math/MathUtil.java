package work.lclpnet.ap2.impl.util.math;

import net.minecraft.util.math.*;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import work.lclpnet.kibu.util.math.Matrix3i;

import java.util.Iterator;
import java.util.Random;

import static java.lang.Math.*;

public class MathUtil {

    public static final double PHI = (1.0 + sqrt(5.0)) / 2.0;

    @SuppressWarnings("UseCompareMethod")
    public static Vec3i normalize(Vec3i blockPos) {
        int x = blockPos.getX(), y = blockPos.getY(), z = blockPos.getZ();

        return new Vec3i(
                (x < 0) ? -1 : ((x == 0) ? 0 : 1),
                (y < 0) ? -1 : ((y == 0) ? 0 : 1),
                (z < 0) ? -1 : ((z == 0) ? 0 : 1)
        );
    }

    public static Vec3d yaw2vec(float yaw) {
        double rad = toRadians(yaw);

        return new Vec3d(sin(-rad), 0, cos(rad));
    }

    public static void yaw2vec(float yaw, Vector3d vec) {
        double rad = toRadians(yaw);

        vec.x = sin(-rad);
        vec.y = 0;
        vec.z = cos(rad);
    }

    public static double angleY(Vec3i dir) {
        return angleY(dir.getX(), dir.getZ());
    }

    public static double angleY(double x, double z) {
        return atan2(x, z);
    }

    public static float yaw(Vector3dc vec) {
        return yaw(vec.x(), vec.z());
    }

    public static float yaw(Vec3d vec) {
        return yaw(vec.getX(), vec.getZ());
    }

    public static float yaw(double x, double z) {
        return (float) toDegrees(angleY(-x, z));
    }

    public static float pitch(Vector3dc vec) {
        return pitch(vec.y());
    }

    public static float pitch(Vec3d vec) {
        return pitch(vec.getY());
    }

    public static float pitch(double y) {
        return (float) toDegrees(asin(-y));
    }

    public static float rotateYaw(float yaw, Matrix3i mat, Vector3d tmp) {
        yaw2vec(yaw, tmp);

        mat.transform(tmp.x, tmp.y, tmp.z, tmp);

        return yaw(tmp);
    }

    public static Vec3d randomUnitVec3d(Random random) {
        float yaw = (float) (random.nextDouble() * PI * 2);
        float pitch = (float) (random.nextDouble() * PI - PI * 0.5);

        float h = MathHelper.cos(-yaw);
        float i = MathHelper.sin(-yaw);
        float j = MathHelper.cos(pitch);
        float k = MathHelper.sin(pitch);

        return new Vec3d(i * j, -k, h * j);
    }

    private MathUtil() {}

    public static int manhattanDistance(BlockPos a, BlockPos b) {
        return abs(a.getX() - b.getX()) + abs(a.getY() - b.getY()) + abs(a.getZ() - b.getZ());
    }

    public static Iterable<Vec3d> corners(Box box) {
        return () -> new Iterator<>() {
            int i = 0;

            @Override
            public boolean hasNext() {
                return i < 8;
            }

            @Override
            public Vec3d next() {
                double x = (i & 1) == 0 ? box.minX : box.maxX;
                double y = (i & 4) == 0 ? box.minY : box.maxY;
                double z = (i & 2) == 0 ? box.minZ : box.maxZ;

                i++;

                return new Vec3d(x, y, z);
            }
        };
    }
}
