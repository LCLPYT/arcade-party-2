package work.lclpnet.ap2.impl.util.math;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import work.lclpnet.kibu.util.math.Matrix3i;

import java.util.Iterator;

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
        MinecraftServer server = player.getServer();
        int viewDistance;

        if (server == null) {
            viewDistance = 2;
        } else {
            viewDistance = Math.max(2, Math.min(player.getViewDistance(), server.getPlayerManager().getViewDistance()));
        }

        Quaterniond rotation = new Quaterniond()
                .rotationYXZ(Math.PI - player.getYaw() * Math.PI / 180.0, -player.getPitch() * Math.PI / 180.0, 0.0F)
                .conjugate();

        int zFar = viewDistance * 16;

        return mat.identity()
                .perspective(fovRadians, screenAspectRatio, 0.05, zFar)
                .rotate(rotation)
                .translate(-player.getX(), -player.getEyeY(), -player.getZ());
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
