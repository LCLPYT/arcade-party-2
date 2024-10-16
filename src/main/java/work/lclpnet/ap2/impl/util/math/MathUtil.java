package work.lclpnet.ap2.impl.util.math;

import net.minecraft.util.math.Vec3i;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import work.lclpnet.kibu.util.math.Matrix3i;

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

    public static void yaw2vec(float yaw, Vector3d vec) {
        double rad = Math.toRadians(yaw);

        vec.x = Math.sin(-rad);
        vec.y = 0;
        vec.z = Math.cos(rad);
    }

    public static float vec2yaw(Vector3dc vec) {
        return (float) Math.toDegrees(Math.atan2(-vec.x(), vec.z()));
    }

    public static float rotateYaw(float yaw, Matrix3i mat, Vector3d tmp) {
        yaw2vec(yaw, tmp);

        mat.transform(tmp.x, tmp.y, tmp.z, tmp);

        return vec2yaw(tmp);
    }

    private MathUtil() {}
}
