package work.lclpnet.ap2.impl.util.math;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import work.lclpnet.kibu.util.math.Matrix3i;

import static java.lang.Math.abs;

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

    public static double angleY(Vec3i dir) {
        return angleY(dir.getX(), dir.getZ());
    }

    public static double angleY(double x, double z) {
        return Math.atan2(x, z);
    }

    public static float vec2yaw(Vector3dc vec) {
        return (float) Math.toDegrees(angleY(-vec.x(), vec.z()));
    }

    public static float rotateYaw(float yaw, Matrix3i mat, Vector3d tmp) {
        yaw2vec(yaw, tmp);

        mat.transform(tmp.x, tmp.y, tmp.z, tmp);

        return vec2yaw(tmp);
    }

    private MathUtil() {}

    public static int manhattanDistance(BlockPos a, BlockPos b) {
        return abs(a.getX() - b.getX()) + abs(a.getY() - b.getY()) + abs(a.getZ() - b.getZ());
    }

    public static Matrix4d viewProjectionMatrix(ServerPlayerEntity player, double fovRadians, double screenAspectRatio, Matrix4d mat) {
        Vec3d _dir = player.getRotationVector();
        Vector3d dir = new Vector3d(_dir.getX(), _dir.getY(), _dir.getZ());

        int zFar = player.getViewDistance() * 16;

        return mat
                .perspective(fovRadians, screenAspectRatio, 0.5, zFar)
                .translate(-player.getX(), -player.getEyeY(), -player.getZ())
                .lookAlong(dir, new Vector3d(0, 1, 0));
    }
}
