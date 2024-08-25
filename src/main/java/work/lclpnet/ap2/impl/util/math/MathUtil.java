package work.lclpnet.ap2.impl.util.math;

import net.minecraft.util.math.Vec3i;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public class MathUtil {

    @SuppressWarnings("UseCompareMethod")
    public static Vec3i normalize(Vec3i blockPos) {
        int x = blockPos.getX(), y = blockPos.getY(), z = blockPos.getZ();

        return new Vec3i(
                (x < 0) ? -1 : ((x == 0) ? 0 : 1),
                (y < 0) ? -1 : ((y == 0) ? 0 : 1),
                (z < 0) ? -1 : ((z == 0) ? 0 : 1)
        );
    }

    public static Quaternionf rotation(Vector3fc orig, Vector3fc dest) {
        var v1 = new Vector3f(orig).normalize();
        var v2 = new Vector3f(dest).normalize();

        float dot = v1.dot(v2);

        if (dot >= 1.0F) {
            // same vectors, use identity quaternion
            return new Quaternionf();
        }

        if (dot <= -1.0F) {
            // opposite vectors, choose arbitrary orthogonal axis as rotation axis
            var axis = v1.cross(1, 0, 0, new Vector3f());

            if (axis.lengthSquared() < 1e-16f) {
                // v1 is perpendicular to x-axis, compute cross product against y-axis instead
                v1.cross(0, 1, 0, axis);
            }

            axis.normalize();

            return new Quaternionf(axis.x, axis.y, axis.z, 0);  // rotate 180 degrees
        }

        v1.cross(v2);

        return new Quaternionf(v1.x, v1.y, v1.z, 1 + dot).normalize();
    }

    private MathUtil() {}
}
