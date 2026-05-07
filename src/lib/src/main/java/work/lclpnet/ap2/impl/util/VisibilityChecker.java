package work.lclpnet.ap2.impl.util;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector4d;
import work.lclpnet.ap2.impl.util.math.MathUtil;

import static java.lang.Math.*;

public class VisibilityChecker {

    public static final double
            PLAYER_FOV = toRadians(90),
            PLAYER_ASPECT_RATIO = 1920 / 1080.d;

    private final BlockGetter blockView;
    @Getter
    private final Matrix4d viewProjMat = new Matrix4d();

    public VisibilityChecker(BlockGetter blockView) {
        this.blockView = blockView;
    }

    public boolean isAnyoneLookingAt(Entity mob, Vec3 pos, Iterable<? extends ServerPlayer> players) {
        return getAnyoneLookingAt(mob, pos, players) != null;
    }

    @Nullable
    public ServerPlayer getAnyoneLookingAt(Entity mob, Vec3 pos, Iterable<? extends ServerPlayer> players) {
        for (ServerPlayer player : players) {
            if (player.isSpectator()) continue;

            if (isVisibleByAt(mob, player, pos)) {
                return player;
            }
        }

        return null;
    }

    public boolean isVisibleByAt(Entity entity, ServerPlayer player, Vec3 pos) {
        viewProjectionMatrix(player, PLAYER_FOV, PLAYER_ASPECT_RATIO, viewProjMat);

        // check if the entity is within the players (estimated) view frustum
        Vec3 playerEyePos = player.getEyePosition();
        Vec3 entityEyePos = new Vec3(pos.x(), pos.y() + entity.getEyeHeight(), pos.z());

        AABB bounds = entity.getDimensions(entity.getPose()).makeBoundingBox(pos);  // add some margin

        return isBoxVisible(playerEyePos, bounds, entityEyePos);
    }

    public boolean isBoxVisible(Vec3 cameraPos, AABB box, Vec3 quickCheckPos) {
        // check if the box is within an estimated view frustum
        Vector4d ndc = new Vector4d();

        if (canSee(viewProjMat, cameraPos, quickCheckPos, ndc)) {
            return true;
        }

        // need to check bounding box corners
        for (Vec3 corner : MathUtil.corners(box)) {
            if (canSee(viewProjMat, cameraPos, corner, ndc)) {
                return true;
            }
        }

        return false;
    }

    public boolean canSee(Matrix4d viewProjMat, Vec3 eyePos, Vec3 pos, Vector4d ndc) {
        ndc.set(pos.x, pos.y, pos.z, 1.0);
        viewProjMat.transform(ndc);
        ndc.div(ndc.w);

        if (abs(ndc.x) > 1.d || abs(ndc.y) > 1.d || abs(ndc.z) > 1.d) return false;

        // within view frustum, check for occlusion
        return !occluded(eyePos, pos, blockView);
    }

    public static boolean occluded(Vec3 from, Vec3 to, BlockGetter view) {
        var hit = BlockGetter.traverseBlocks(from, to, null, (ctx, pos) -> {
            BlockState state = view.getBlockState(pos);

            // ray should pass through non-opaque blocks
            if (!state.canOcclude()) {
                return null;
            }

            VoxelShape shape = ClipContext.Block.VISUAL.get(state, view, pos, CollisionContext.empty());

            return view.clipWithInteractionOverride(from, to, pos, shape, state);
        }, o -> {
            Vec3 dir = from.subtract(to);
            return BlockHitResult.miss(to, Direction.getApproximateNearest(dir.x, dir.y, dir.z), BlockPos.containing(to));
        });

        return hit != null && hit.getType() != HitResult.Type.MISS;
    }

    public static Matrix4d viewProjectionMatrix(ServerPlayer player, double fovRadians, double screenAspectRatio, Matrix4d mat) {
        MinecraftServer server = player.level().getServer();

        int viewDistance = max(2, min(player.requestedViewDistance(), server.getPlayerList().getViewDistance()));

        return viewProjectionMatrix(player.getX(), player.getEyeY(), player.getZ(), player.getYRot(), player.getXRot(),
                viewDistance, fovRadians, screenAspectRatio, mat);
    }

    public static Matrix4d viewProjectionMatrix(double cameraX, double cameraY, double cameraZ, float yaw, float pitch,
                                                int viewDistance, double fovRadians, double screenAspectRatio, Matrix4d mat) {

        Quaterniond rotation = new Quaterniond()
                .rotationYXZ(Math.PI - yaw * Math.PI / 180.0, -pitch * Math.PI / 180.0, 0.0F)
                .conjugate();

        int zFar = viewDistance * 16;

        return mat.identity()
                .perspective(fovRadians, screenAspectRatio, 0.05, zFar)
                .rotate(rotation)
                .translate(-cameraX, -cameraY, -cameraZ);
    }
}
