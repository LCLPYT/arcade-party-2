package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector4d;
import work.lclpnet.ap2.impl.util.math.MathUtil;

import static java.lang.Math.abs;
import static java.lang.Math.toRadians;

public class VisibilityChecker {

    public static final double
            PLAYER_FOV = toRadians(90),
            PLAYER_ASPECT_RATIO = 1920 / 1080.d;

    private final BlockView blockView;
    private final Matrix4d viewProjMat = new Matrix4d();

    public VisibilityChecker(BlockView blockView) {
        this.blockView = blockView;
    }

    public boolean isAnyoneLookingAt(Entity mob, Vec3d pos, Iterable<? extends ServerPlayerEntity> players) {
        return getAnyoneLookingAt(mob, pos, players) != null;
    }

    @Nullable
    public ServerPlayerEntity getAnyoneLookingAt(Entity mob, Vec3d pos, Iterable<? extends ServerPlayerEntity> players) {
        for (ServerPlayerEntity player : players) {
            if (player.isSpectator()) continue;

            if (isVisibleByAt(mob, player, pos)) {
                return player;
            }
        }

        return null;
    }

    public boolean isVisibleByAt(Entity entity, ServerPlayerEntity player, Vec3d pos) {
        // check if the entity is within the players (estimated) view frustum
        Vector4d ndc = new Vector4d();
        Vec3d playerEyePos = player.getEyePos();

        viewProjectionMatrix(player, PLAYER_FOV, PLAYER_ASPECT_RATIO, viewProjMat);

        // first, check entity eye pos
        Vec3d entityEyePos = new Vec3d(pos.getX(), pos.getY() + entity.getStandingEyeHeight(), pos.getZ());

        if (canSee(viewProjMat, playerEyePos, entityEyePos, ndc)) {
            return true;
        }

        // else, check bounding box corners
        Box bounds = entity.getDimensions(entity.getPose()).getBoxAt(pos);  // add some margin

        for (Vec3d corner : MathUtil.corners(bounds)) {
            if (canSee(viewProjMat, playerEyePos, corner, ndc)) {
                return true;
            }
        }

        return false;
    }

    public boolean canSee(Matrix4d viewProjMat, Vec3d eyePos, Vec3d pos, Vector4d ndc) {
        ndc.set(pos.x, pos.y, pos.z, 1.0);
        viewProjMat.transform(ndc);
        ndc.div(ndc.w);

        if (abs(ndc.x) > 1.d || abs(ndc.y) > 1.d || abs(ndc.z) > 1.d) return false;

        // within view frustum, check for occlusion
        return !occluded(eyePos, pos, blockView);
    }

    public static boolean occluded(Vec3d from, Vec3d to, BlockView view) {
        var hit = BlockView.raycast(from, to, null, (ctx, pos) -> {
            BlockState state = view.getBlockState(pos);

            // ray should pass through non-opaque blocks
            if (!state.isOpaque()) {
                return null;
            }

            VoxelShape shape = RaycastContext.ShapeType.VISUAL.get(state, view, pos, ShapeContext.absent());

            return view.raycastBlock(from, to, pos, shape, state);
        }, o -> {
            Vec3d dir = from.subtract(to);
            return BlockHitResult.createMissed(to, Direction.getFacing(dir.x, dir.y, dir.z), BlockPos.ofFloored(to));
        });

        return hit.getType() != HitResult.Type.MISS;
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
}
