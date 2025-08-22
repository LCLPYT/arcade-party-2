package work.lclpnet.ap2.game.maze_scape.monster.behaviour;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import work.lclpnet.ap2.game.maze_scape.setup.OrientedStructurePiece;
import work.lclpnet.ap2.game.maze_scape.util.MSManager;
import work.lclpnet.ap2.game.maze_scape.util.Passage;
import work.lclpnet.ap2.impl.scene.Object3d;
import work.lclpnet.kibu.scheduler.Ticks;

import java.util.List;
import java.util.Set;

public class UnstuckBehaviour implements MonsterBehaviour {

    private static final int
            CHECK_STUCK_TICKS = Ticks.seconds(2),
            POSITION_SAMPLE_SIZE = 30,
            MAX_FAILED_UNSTUCK_ATTEMPTS = 4;
    private static final boolean
            DEBUG_AVG_POS = false;

    private final MSManager manager;
    private final double stuckTolSq;
    private final PosBuf posBuf = new PosBuf(POSITION_SAMPLE_SIZE);
    private final Vector3d prevAvgPos = new Vector3d(0);
    private boolean enabled = true;
    private int unstuckFailCount = 0;
    private int checkStuckTimer = 0;
    private @Nullable Passage lastUnstuck = null;
    private @Nullable Object3d avgPosMarker = null;

    public UnstuckBehaviour(MSManager manager, double stuckTol) {
        this.manager = manager;
        this.stuckTolSq = stuckTol * stuckTol;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void init(MobEntity mob) {
        if (DEBUG_AVG_POS) {
            Vec3d pos = mob != null ? mob.getPos() : Vec3d.ZERO;

            avgPosMarker = manager.debugController().parent().renderer()
                    .map(renderer -> renderer.marker(pos.x, pos.y, pos.z, Blocks.GREEN_CONCRETE.getDefaultState(), 0x00ff00))
                    .orElse(null);
        }
    }

    @Override
    public void tick(MobEntity mob) {
        posBuf.update(mob.getPos());

        if (DEBUG_AVG_POS && avgPosMarker != null) {
            avgPosMarker.position.set(posBuf.avg.x, posBuf.avg.y, posBuf.avg.z);
            avgPosMarker.updateMatrixWorld();
        }

        if (enabled && checkStuckTimer++ % CHECK_STUCK_TICKS == 0) {
            if (prevAvgPos.distanceSquared(posBuf.avg) < stuckTolSq) {
                unstuck(mob);
            }

            prevAvgPos.set(posBuf.avg);
        }
    }

    private void unstuck(MobEntity mob) {
        LivingEntity target = mob.getTarget();

        if (target == null) return;

        var navPath = manager.struct().findPath(mob.getPos(), target.getPos());

        if (navPath.isEmpty()) return;

        List<Passage> passagePath = navPath.get().path();

        if (passagePath.size() < 2) {
            if (++unstuckFailCount >= MAX_FAILED_UNSTUCK_ATTEMPTS) {
                unstuckFailCount = 0;
                teleport(mob, target.getPos());
            }

            return;
        }

        unstuckFailCount = 0;

        Passage first = passagePath.getFirst();
        Passage second = passagePath.get(1);

        var commonNode = first.commonNode(second);

        if (commonNode != null) {
            OrientedStructurePiece oriented = commonNode.oriented();

            if (oriented != null && oriented.piece().noUnstuck()) {
                return;
            }
        }

        Passage next = second;

        if (second == lastUnstuck && passagePath.size() >= 3) {
            next = passagePath.get(2);
        }

        lastUnstuck = next;

        teleport(mob, next.pos().toBottomCenterPos());
    }

    static void teleport(Entity entity, Vec3d pos) {
        if (entity.getWorld() instanceof ServerWorld world) {
            entity.teleport(world, pos.getX(), pos.getY(), pos.getZ(), Set.of(), entity.getYaw(), entity.getPitch(), true);
        }
    }

    private static class PosBuf {
        final Vector3d[] buf;
        final Vector3d avg;
        int cursor = 0;
        int count = 0;

        public PosBuf(int size) {
            buf = new Vector3d[size];

            for (int i = 0; i < size; i++) {
                buf[i] = new Vector3d(0);
            }

            avg = new Vector3d(0);
        }

        public void update(Vec3d pos) {
            buf[cursor].set(pos.getX(), pos.getY(), pos.getZ());
            cursor = (cursor + 1) % buf.length;
            count = Math.min(count + 1, buf.length);

            avg.zero();

            for (int i = 0; i < count; i++) {
                avg.add(buf[i]);
            }

            if (count > 1) {
                avg.div(count);
            }
        }
    }
}
