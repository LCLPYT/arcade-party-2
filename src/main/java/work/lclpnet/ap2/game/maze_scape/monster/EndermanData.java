package work.lclpnet.ap2.game.maze_scape.monster;

import net.minecraft.block.ShapeContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;
import org.joml.Vector4d;
import work.lclpnet.ap2.core.mixin.EndermanEntityAccessor;
import work.lclpnet.ap2.impl.util.math.MathUtil;

import static java.lang.Math.abs;

public class EndermanData implements MonsterData {

    private static final int
            VISIBLE_CHECK_INTERVAL_TICKS = 10,
            SCARED_TICKS = 18;
    private static final double
            PLAYER_FOV = Math.toRadians(90),
            PLAYER_ASPECT_RATIO = 1920 / 1080.d;

    private final CommonData common;
    private int visibleCheckTimer = 0;
    private boolean scared = false;
    private int scaredTimer = 0;

    public EndermanData(MonsterArgs args) {
        common = new CommonData(args, 0.35, 0.45, 0.75);
    }

    @Override
    public void init() {
        common.init();
    }

    @Override
    public void tick() {
        common.tick();

        if (visibleCheckTimer++ == VISIBLE_CHECK_INTERVAL_TICKS) {
            visibleCheckTimer = 0;
            checkVisible();
        }

        if (scaredTimer > 0 && --scaredTimer == 0) {
            setScared(false);
        }
    }

    @Override
    public void onKillAcquired() {
        common.onKillAcquired();
    }

    @Override
    public @Nullable EndermanEntity mob() {
        if (common.mob() instanceof EndermanEntity enderman) {
            return enderman;
        }

        return null;
    }

    private void checkVisible() {
        var enderman = mob();

        if (enderman == null) return;

        LivingEntity target = enderman.getTarget();

        if (!(target instanceof ServerPlayerEntity player)) return;

        if (isVisibleBy(enderman, player)) {
            onLookedAt(enderman, player);
        }
    }

    private boolean isVisibleBy(EndermanEntity enderman, ServerPlayerEntity player) {
        // check if the enderman is within the players (estimated) view frustum
        Matrix4d mat = MathUtil.viewProjectionMatrix(player, PLAYER_FOV, PLAYER_ASPECT_RATIO, new Matrix4d());

        Vector4d ndc = new Vector4d(enderman.getX(), enderman.getEyeY(), enderman.getZ(), 1d);

        mat.transform(ndc);

        ndc.div(ndc.w);

        if (abs(ndc.x) > 1.d || abs(ndc.y) > 1.d || abs(ndc.z) > 1.d) return false;

        // check for occlusion
        Vec3d from = player.getEyePos();
        Vec3d to = enderman.getEyePos();

        BlockHitResult hit = common.manager().world()
                .raycast(new RaycastContext(from, to, RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.ANY, ShapeContext.absent()));

        return hit.getType() == HitResult.Type.MISS;
    }

    private void onLookedAt(EndermanEntity enderman, ServerPlayerEntity player) {
        setScared(true);
        scaredTimer = SCARED_TICKS;
    }

    public boolean isScared() {
        return scared;
    }

    private void setScared(boolean scared) {
        this.scared = scared;

        EndermanEntity mob = mob();

        if (mob != null) {
            mob.getDataTracker().set(EndermanEntityAccessor.ANGRY(), scared);
        }
    }
}
