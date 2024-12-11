package work.lclpnet.ap2.game.maze_scape.monster;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import work.lclpnet.ap2.game.maze_scape.setup.OrientedStructurePiece;
import work.lclpnet.ap2.game.maze_scape.util.MSManager;
import work.lclpnet.kibu.scheduler.Ticks;

import java.util.Set;
import java.util.UUID;

class CommonData implements MonsterData {

    private static final int UNSTUCK_TICKS = Ticks.seconds(5);
    private static final double STUCK_TOL_SQ = 0.25 * 0.25;
    private final UUID uuid;
    private final MSManager manager;
    private final Logger logger;
    private @Nullable Vec3d prevPos = null;
    private int stuckTimer = 0;

    public CommonData(UUID uuid, MSManager manager, Logger logger) {
        this.uuid = uuid;
        this.manager = manager;
        this.logger = logger;
    }

    @Nullable
    public MobEntity mob() {
        if (manager.world().getEntity(uuid) instanceof MobEntity mob) {
            return mob;
        }

        return null;
    }

    public MSManager manager() {
        return manager;
    }

    public UUID uuid() {
        return uuid;
    }

    public int stuckTimer() {
        return stuckTimer;
    }

    public void tick() {
        MobEntity mob = mob();

        if (mob == null) return;

        validatePos(mob);

        Vec3d prevPos = this.prevPos;
        Vec3d pos = mob.getPos();

        this.prevPos = pos;

        if (prevPos != null && prevPos.squaredDistanceTo(pos) < STUCK_TOL_SQ) {
            if (stuckTimer++ >= UNSTUCK_TICKS) {
                stuckTimer = 0;
                unstuck(mob);
            }
        } else {
            stuckTimer = 0;
        }
    }

    private void unstuck(MobEntity mob) {
        LivingEntity target = mob.getTarget();

        if (target == null) return;

        var passagePath = manager.findPassagePath(mob, target.getBlockPos());

        if (passagePath.size() < 2) return;

        teleport(mob, passagePath.get(1).pos().toBottomCenterPos());
    }

    private void validatePos(Entity entity) {
        var node = manager.struct().nodeAt(entity.getX(), entity.getY(), entity.getZ());

        if (node == null) {
            teleportToDistantPos(entity);
            return;
        }

        OrientedStructurePiece oriented = node.oriented();

        if (oriented == null) {
            teleportToDistantPos(entity);
            return;
        }

        if (oriented.isPitAt(entity.getBlockX(), entity.getBlockY(), entity.getBlockZ())) {
            Vec3d spawn = oriented.spawn();

            if (spawn == null) {
                teleportToDistantPos(entity);
                return;
            }

            teleport(entity, spawn);
        }
    }

    private void teleportToDistantPos(Entity entity) {
        var spawns = manager.mostDistantSpawns();

        if (spawns.isEmpty()) {
            logger.error("Could not find distant position");
            return;
        }

        teleport(entity, spawns.getFirst());
    }

    private void teleport(Entity entity, Vec3d pos) {
        entity.teleport(manager.world(), pos.getX(), pos.getY(), pos.getZ(), Set.of(), entity.getYaw(), entity.getPitch());
    }
}
