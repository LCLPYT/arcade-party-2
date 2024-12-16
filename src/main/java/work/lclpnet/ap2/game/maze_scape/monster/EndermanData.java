package work.lclpnet.ap2.game.maze_scape.monster;

import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector4d;
import org.slf4j.Logger;
import work.lclpnet.ap2.game.maze_scape.util.MSManager;

import java.util.UUID;

import static java.lang.Math.abs;

public class EndermanData implements MonsterData {

    private static final int VISIBLE_CHECK_INTERVAL_TICKS = 10;
    private static final double
            PLAYER_FOV = Math.toRadians(90),
            PLAYER_ASPECT_RATIO = 1920 / 1080.d;

    private final CommonData common;
    private int visibleCheckTimer = 0;

    public EndermanData(UUID uuid, MSManager manager, Logger logger) {
        common = new CommonData(uuid, manager, logger);
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
        Vec3d _dir = player.getRotationVector();
        Vector3d dir = new Vector3d(_dir.getX(), _dir.getY(), _dir.getZ());

        int zFar = player.getViewDistance() * 16;
        var mat = new Matrix4d()
                .perspective(PLAYER_FOV, PLAYER_ASPECT_RATIO, 0.5, zFar)
                .translate(-player.getX(), -player.getY(), -player.getZ())
                .lookAlong(dir, new Vector3d(0, 1, 0));

        Vector4d pos = new Vector4d(enderman.getX(), enderman.getEyeY(), enderman.getZ(), 1d);

        mat.transform(pos);

        pos.div(pos.w);

        if (abs(pos.x) > 1.d || abs(pos.y) > 1.d || abs(pos.z) > 1.d) return false;

        // check for occlusion
        HitResult hit = player.raycast(zFar + 1, 0, true);

        return hit instanceof EntityHitResult entityHit && entityHit.getEntity() == enderman;
    }

    private void onLookedAt(EndermanEntity enderman, ServerPlayerEntity player) {
        // TODO
    }
}
