package work.lclpnet.ap2.game.maze_scape.monster;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import work.lclpnet.ap2.game.maze_scape.setup.OrientedStructurePiece;
import work.lclpnet.ap2.game.maze_scape.util.MSManager;
import work.lclpnet.ap2.game.maze_scape.util.MSStruct;
import work.lclpnet.ap2.impl.util.EntityUtil;
import work.lclpnet.kibu.scheduler.Ticks;

import java.util.Set;
import java.util.UUID;

import static net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED;

class CommonData implements MonsterData {

    private static final int UNSTUCK_TICKS = Ticks.seconds(5);
    private static final double
            ACCELERATION_DISTANCE_SQ = 16 * 16,
            ACCELERATION_PER_TICK = 2.8125E-4;

    private final UUID uuid;
    private final MSManager manager;
    private final Logger logger;
    private final double baseSpeed, maxSpeed, stuckTolSq;
    private @Nullable Vec3d prevPos = null;
    private int stuckTimer = 0;
    private int sameRoomTimer = 0;

    public CommonData(UUID uuid, MSManager manager, Logger logger, double baseSpeed, double maxSpeed, double stuckTol) {
        this.uuid = uuid;
        this.manager = manager;
        this.logger = logger;
        this.baseSpeed = baseSpeed;
        this.maxSpeed = maxSpeed;
        this.stuckTolSq = stuckTol * stuckTol;
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

    @Override
    public void init() {
        resetSpeed();
    }

    @Override
    public void tick() {
        MobEntity mob = mob();

        if (mob == null) return;

        validatePos(mob);

        Vec3d prevPos = this.prevPos;
        Vec3d pos = mob.getPos();

        this.prevPos = pos;

        if (prevPos != null && prevPos.squaredDistanceTo(pos) < stuckTolSq) {
            if (stuckTimer++ >= UNSTUCK_TICKS) {
                stuckTimer = 0;
                unstuck(mob);
            }
        } else {
            stuckTimer = 0;
            accelerate(mob);
        }
    }

    @Override
    public void onKillAcquired() {
        resetSpeed();
    }

    protected boolean sameRoomTimerDue(int timeout) {
        MobEntity mob = mob();

        if (mob == null) return false;

        LivingEntity target = mob.getTarget();

        if (target != null && isInSameRoom(mob.getPos(), target.getPos())) {
            if (sameRoomTimer++ >= timeout) {
                sameRoomTimer = 0;
                return true;
            }
        } else {
            sameRoomTimer = 0;
        }

        return false;
    }

    private void resetSpeed() {
        MobEntity mob = mob();

        if (mob == null) return;

        EntityUtil.setAttribute(mob, GENERIC_MOVEMENT_SPEED, baseSpeed);
    }

    private void accelerate(MobEntity mob) {
        LivingEntity target = mob.getTarget();

        if (target == null || mob.squaredDistanceTo(target) > ACCELERATION_DISTANCE_SQ) return;

        double currentSpeed = mob.getAttributeBaseValue(GENERIC_MOVEMENT_SPEED);
        double newSpeed = Math.min(currentSpeed + ACCELERATION_PER_TICK, maxSpeed);

        EntityUtil.setAttribute(mob, GENERIC_MOVEMENT_SPEED, newSpeed);
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

    private boolean isInSameRoom(Vec3d first, Vec3d second) {
        MSStruct struct = manager.struct();

        var wardenNode = struct.nodeAt(first);
        var targetNode = struct.nodeAt(second);

        return wardenNode != null && wardenNode == targetNode;
    }
}
