package work.lclpnet.ap2.game.maze_scape.monster;

import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;
import org.joml.Vector4d;
import work.lclpnet.ap2.impl.util.math.MathUtil;

import static java.lang.Math.abs;

public class EndermanData implements MonsterData {

    private static final int VISIBLE_CHECK_INTERVAL_TICKS = 10;
    private static final double
            PLAYER_FOV = Math.toRadians(90),
            PLAYER_ASPECT_RATIO = 1920 / 1080.d;

    private final CommonData common;
    private int visibleCheckTimer = 0;

    public EndermanData(MonsterArgs args) {
        common = new CommonData(args, 0.3, 0.45, 0.2);
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
    }

    @Override
    public void onKillAcquired() {
        common.onKillAcquired();
    }

    private @Nullable EndermanEntity enderman() {
        if (common.mob() instanceof EndermanEntity enderman) {
            return enderman;
        }

        return null;
    }

    private void checkVisible() {
        var enderman = enderman();

        if (enderman == null) return;

        for (ServerPlayerEntity player : common.manager().participants()) {
            if (isVisibleBy(enderman, player)) {
                onLookedAt(enderman, player);
                return;
            }
        }
    }

    private boolean isVisibleBy(EndermanEntity enderman, ServerPlayerEntity player) {
        // check if the enderman is within the players (estimated) view frustum
        Matrix4d mat = MathUtil.viewProjectionMatrix(player, PLAYER_FOV, PLAYER_ASPECT_RATIO, new Matrix4d());

        Vector4d pos = new Vector4d(enderman.getX(), enderman.getEyeY(), enderman.getZ(), 1d);

        mat.transform(pos);

        pos.div(pos.w);

        if (abs(pos.x) > 1.d || abs(pos.y) > 1.d || abs(pos.z) > 1.d) return false;

        // check for occlusion
        HitResult hit = player.raycast(player.getViewDistance() * 16 + 1, 0, true);

        return hit instanceof EntityHitResult entityHit && entityHit.getEntity() == enderman;
    }

    private void onLookedAt(EndermanEntity enderman, ServerPlayerEntity player) {
        // TODO
    }
}
