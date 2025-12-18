package work.lclpnet.ap2.impl.util.math;

import com.google.common.collect.AbstractIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
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

    public static Vec3 yaw2vec(float yaw) {
        double rad = toRadians(yaw);

        return new Vec3(sin(-rad), 0, cos(rad));
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

    public static float yaw(Vec3 vec) {
        return yaw(vec.x(), vec.z());
    }

    public static float yaw(double x, double z) {
        return (float) toDegrees(angleY(-x, z));
    }

    public static float pitch(Vector3dc vec) {
        return pitch(vec.y());
    }

    public static float pitch(Vec3 vec) {
        return pitch(vec.y());
    }

    public static float pitch(double y) {
        return (float) toDegrees(asin(-y));
    }

    public static float rotateYaw(float yaw, Matrix3i mat, Vector3d tmp) {
        yaw2vec(yaw, tmp);

        mat.transform(tmp.x, tmp.y, tmp.z, tmp);

        return yaw(tmp);
    }

    public static Vec3 randomUnitVec3d(Random random) {
        float yaw = (float) (random.nextDouble() * PI * 2);
        float pitch = (float) (random.nextDouble() * PI - PI * 0.5);

        float h = Mth.cos(-yaw);
        float i = Mth.sin(-yaw);
        float j = Mth.cos(pitch);
        float k = Mth.sin(pitch);

        return new Vec3(i * j, -k, h * j);
    }

    private MathUtil() {}

    public static int manhattanDistance(BlockPos a, BlockPos b) {
        return abs(a.getX() - b.getX()) + abs(a.getY() - b.getY()) + abs(a.getZ() - b.getZ());
    }

    public static Iterable<Vec3> corners(AABB box) {
        return () -> new Iterator<>() {
            int i = 0;

            @Override
            public boolean hasNext() {
                return i < 8;
            }

            @Override
            public Vec3 next() {
                double x = (i & 1) == 0 ? box.minX : box.maxX;
                double y = (i & 4) == 0 ? box.minY : box.maxY;
                double z = (i & 2) == 0 ? box.minZ : box.maxZ;

                i++;

                return new Vec3(x, y, z);
            }
        };
    }

    /**
     * Apply a random offset to a unit direction vector.
     * @param dir The direction unit vector.
     * @param spread The maximum angle between the input vector and the output vector, in radians.
     * @param random The RNG instance.
     * @return A randomly offset unit vector, based on the input vector and the spread.
     */
    public static Vec3 applySpread(Vec3 dir, double spread, Random random) {
        double cosMax = cos(spread);
        double cosTheta = cosMax + (1 - cosMax) * random.nextDouble();
        double sinTheta = sqrt(1 - cosTheta * cosTheta);

        double phi = random.nextDouble() * 2 * PI;

        Vec3 t = new Vec3(1, 0, 0);

        if (abs(dir.dot(t)) > 0.999) {
            t = new Vec3(0, 1, 0);
        }

        Vec3 axis = dir.cross(t).normalize();
        Vec3 perp = dir.cross(axis).normalize();

        return axis.scale(cos(phi) * sinTheta)
                .add(perp.scale(sin(phi) * sinTheta))
                .add(dir.scale(cosTheta))
                .normalize();
    }

    public static Iterable<Vector3f> fibonacciHemisphere(int samples) {
        double phi = PI * (3 - sqrt(5));

        return () -> new AbstractIterator<>() {
            final Vector3f vec = new Vector3f();
            int i = 0;

            @Override
            protected Vector3f computeNext() {
                if (i >= samples) {
                    endOfData();
                    return null;
                }

                double y = (double) i / (samples - 1);  // [0..1] for upper hemisphere
                double r = sqrt(1 - y * y);
                double theta = (i * phi) % (2 * PI);

                vec.x = (float) (r * cos(theta));
                vec.z = (float) (r * sin(theta));
                vec.y = (float) y;

                i++;

                return vec;
            }
        };
    }
}
