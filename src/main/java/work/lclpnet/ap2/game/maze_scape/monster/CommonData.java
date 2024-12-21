package work.lclpnet.ap2.game.maze_scape.monster;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.slf4j.Logger;
import work.lclpnet.ap2.game.maze_scape.setup.OrientedStructurePiece;
import work.lclpnet.ap2.game.maze_scape.util.MSManager;
import work.lclpnet.ap2.game.maze_scape.util.MSStruct;
import work.lclpnet.ap2.game.maze_scape.util.Passage;
import work.lclpnet.ap2.impl.util.EntityUtil;
import work.lclpnet.kibu.access.entity.DisplayEntityAccess;
import work.lclpnet.kibu.scheduler.Ticks;

import java.util.Set;
import java.util.UUID;

import static net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED;

class CommonData implements MonsterData {

    private static final int
            UNSTUCK_TICKS = Ticks.seconds(5),
            POSITION_SAMPLE_SIZE = 80;
    private static final boolean
            DEBUG_AVG_POS = true;
    private static final double
            ACCELERATION_DISTANCE_SQ = 16 * 16,
            ACCELERATION_PER_TICK = 2.8125E-4;

    private final UUID uuid;
    private final MSManager manager;
    private final Logger logger;
    private final double baseSpeed, maxSpeed, stuckTolSq;
    private final PosBuf posBuf = new PosBuf(POSITION_SAMPLE_SIZE);
    private final Vector3d prevAvgPos = new Vector3d(0);
    private int stuckTimer = 0;
    private int sameRoomTimer = 0;
    private @Nullable DisplayEntity.BlockDisplayEntity avgPosDisplay = null;

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

        if (DEBUG_AVG_POS) {
            ServerWorld world = manager.world();
            avgPosDisplay = new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY, world);
            avgPosDisplay.setGlowing(true);

            DisplayEntityAccess.setBlockState(avgPosDisplay, Blocks.GREEN_CONCRETE.getDefaultState());
            DisplayEntityAccess.setGlowColorOverride(avgPosDisplay, 0x00ff00);
            DisplayEntityAccess.setTransformation(avgPosDisplay, new AffineTransformation(new Matrix4f().scale(.25f)));

            MobEntity mob = mob();

            if (mob != null) {
                avgPosDisplay.setPosition(mob.getPos());
            }

            world.spawnEntity(avgPosDisplay);
        }
    }

    @Override
    public void tick() {
        MobEntity mob = mob();

        if (mob == null) return;

        validatePos(mob);

        posBuf.update(mob.getPos());

        if (DEBUG_AVG_POS && avgPosDisplay != null) {
            avgPosDisplay.teleport(manager.world(), posBuf.avg.x, posBuf.avg.y, posBuf.avg.z, Set.of(), 0, 0);
        }

        if (prevAvgPos.distanceSquared(posBuf.avg) < stuckTolSq) {
            if (stuckTimer++ >= UNSTUCK_TICKS) {
                stuckTimer = 0;
                unstuck(mob);
            }
        } else {
            stuckTimer = 0;
            accelerate(mob);
        }

        prevAvgPos.set(posBuf.avg);
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

        var startNode = manager.struct().nodeAt(mob.getPos());

        if (startNode == null) return;

        OrientedStructurePiece oriented = startNode.oriented();

        if (oriented == null || oriented.piece().noUnstuck()) return;

        var passagePath = manager.findPassagePath(mob, target.getBlockPos());

        if (passagePath.size() < 2) return;

        Passage first = passagePath.get(0);
        Passage second = passagePath.get(1);

        var commonNode = first.commonNode(second);

        // if the first two passages share the start node, use the second passage as teleport target
        int index = commonNode == startNode ? 1 : 0;

        teleport(mob, passagePath.get(index).pos().toBottomCenterPos());
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
