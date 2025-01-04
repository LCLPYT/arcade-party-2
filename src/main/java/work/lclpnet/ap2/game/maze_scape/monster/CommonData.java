package work.lclpnet.ap2.game.maze_scape.monster;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import work.lclpnet.ap2.game.maze_scape.setup.OrientedStructurePiece;
import work.lclpnet.ap2.game.maze_scape.util.MSManager;
import work.lclpnet.ap2.game.maze_scape.util.MSStruct;
import work.lclpnet.ap2.impl.util.EntityUtil;

import java.util.Set;
import java.util.UUID;

import static net.minecraft.entity.attribute.EntityAttributes.MOVEMENT_SPEED;

class CommonData implements MonsterData<MobEntity> {

    private static final double
            ACCELERATION_DISTANCE_SQ = 16 * 16,
            ACCELERATION_PER_TICK = 2.0E-4;

    private final UUID uuid;
    private final MSManager manager;
    private final Logger logger;
    private final double baseSpeed, maxSpeed;
    private int sameRoomTimer = 0;

    public CommonData(MonsterArgs args, double baseSpeed, double maxSpeed) {
        this.uuid = args.uuid();
        this.manager = args.manager();
        this.logger = args.logger();
        this.baseSpeed = baseSpeed;
        this.maxSpeed = maxSpeed;
    }

    @Override
    public @Nullable MobEntity mob() {
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
    public void init(MobEntity mob) {
        resetSpeed(mob);
    }

    @Override
    public void tick(MobEntity mob) {
        validatePos(mob);
        accelerate(mob);
    }

    @Override
    public void onKillAcquired(MobEntity mob) {
        resetSpeed(mob);
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

    private void resetSpeed(MobEntity mob) {
        EntityUtil.setAttribute(mob, MOVEMENT_SPEED, baseSpeed);
    }

    private void accelerate(MobEntity mob) {
        LivingEntity target = mob.getTarget();

        if (target == null || mob.squaredDistanceTo(target) > ACCELERATION_DISTANCE_SQ) return;

        double currentSpeed = mob.getAttributeBaseValue(MOVEMENT_SPEED);
        double newSpeed = Math.min(currentSpeed + ACCELERATION_PER_TICK, maxSpeed);

        EntityUtil.setAttribute(mob, MOVEMENT_SPEED, newSpeed);
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
        var spawns = manager.spawns();

        if (spawns == null) {
            logger.error("Could not find distant position");
            return;
        }

        teleport(entity, spawns.get());
    }

    static void teleport(Entity entity, Vec3d pos) {
        if (entity.getWorld() instanceof ServerWorld world) {
            entity.teleport(world, pos.getX(), pos.getY(), pos.getZ(), Set.of(), entity.getYaw(), entity.getPitch(), true);
        }
    }

    private boolean isInSameRoom(Vec3d first, Vec3d second) {
        MSStruct struct = manager.struct();

        var wardenNode = struct.nodeAt(first);
        var targetNode = struct.nodeAt(second);

        return wardenNode != null && wardenNode == targetNode;
    }
}
