package work.lclpnet.ap2.game.maze_scape.monster;

import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;
import org.joml.Vector4d;
import work.lclpnet.ap2.base.ArcadeParty;
import work.lclpnet.ap2.core.mixin.EndermanEntityAccessor;
import work.lclpnet.ap2.game.maze_scape.gen.Node;
import work.lclpnet.ap2.game.maze_scape.setup.Connector3;
import work.lclpnet.ap2.game.maze_scape.setup.OrientedStructurePiece;
import work.lclpnet.ap2.game.maze_scape.setup.StructurePiece;
import work.lclpnet.ap2.game.maze_scape.util.MSStruct;
import work.lclpnet.ap2.game.maze_scape.util.Passage;
import work.lclpnet.ap2.impl.util.math.MathUtil;
import work.lclpnet.kibu.scheduler.Ticks;

import java.util.*;
import java.util.function.Predicate;

import static java.lang.Math.abs;

public class EndermanData implements MonsterData {

    private static final int
            VISIBLE_CHECK_INTERVAL_TICKS = 5,
            SCARED_TICKS = 16,
            FLEE_TIMEOUT_TICKS = Ticks.seconds(5);
    private static final double
            PLAYER_FOV = Math.toRadians(90),
            PLAYER_ASPECT_RATIO = 1920 / 1080.d,
            FLEE_SPEED_BONUS = 0.1,
            FLEE_MIN_ANGLE_DEG = 65;
    private static final boolean
            DEBUG_FLEE_POSITIONS = false,
            DEBUG_TARGET_FLEE_POS = false,
            DEBUG_FLEE_PATHS = false;
    private static final Identifier FLEE_BONUS_ID = ArcadeParty.identifier("flee_bonus");

    private final MonsterArgs args;
    private final CommonData common;
    private final MSStruct struct;
    private int visibleCheckTimer = 0;
    private boolean scared = false;
    private int scaredTimer = 0;
    private final Matrix4d mat = new Matrix4d();
    private @Nullable BlockPos fleeTargetPos = null;
    private int fleeTargetTimeout = 0;

    public EndermanData(MonsterArgs args, MSStruct struct) {
        this.args = args;
        this.common = new CommonData(args, 0.35, 0.45, 0.75);
        this.struct = struct;
    }

    @Override
    public void init() {
        common.init();
    }

    @Override
    public void tick() {
        common.setUnstuckEnabled(fleeTargetPos == null);

        common.tick();

        EndermanEntity mob = mob();

        if (mob == null) return;

        if (visibleCheckTimer++ == VISIBLE_CHECK_INTERVAL_TICKS) {
            visibleCheckTimer = 0;
            checkVisible();
        }

        if (scaredTimer > 0 && --scaredTimer == 0) {
            setScared(false);
        }

        if (fleeTargetTimeout > 0) {
            if (--fleeTargetTimeout == 0) {
                stopFleeing(mob);
            }
        } else if (fleeTargetPos != null && fleeTargetPos.getSquaredDistance(mob.getPos()) <= 1.44)  {
            fleeTargetTimeout = FLEE_TIMEOUT_TICKS;
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

        // update view-projection matrix with current data
        MathUtil.viewProjectionMatrix(player, PLAYER_FOV, PLAYER_ASPECT_RATIO, mat);

        if (isVisibleBy(enderman, player)) {
            onLookedAt(enderman, player);
        }
    }

    private boolean isVisibleBy(EndermanEntity enderman, ServerPlayerEntity player) {
        return isVisibleByAt(enderman, player, enderman.getPos());
    }

    private boolean isVisibleByAt(EndermanEntity enderman, ServerPlayerEntity player, Vec3d pos) {
        // check if the enderman is within the players (estimated) view frustum
        Vec3d to = pos.add(0, enderman.getStandingEyeHeight(), 0);

        // TODO consider visibility box of enderman, grown by some margin
        // TODO only consider opaque blocks vision blocking

        Vector4d ndc = new Vector4d(to.x, to.y, to.z, 1d);

        mat.transform(ndc);

        ndc.div(ndc.w);

        if (abs(ndc.x) > 1.d || abs(ndc.y) > 1.d || abs(ndc.z) > 1.d) return false;

        // check for occlusion
        Vec3d from = player.getEyePos();

        BlockHitResult hit = common.manager().world()
                .raycast(new RaycastContext(from, to, RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.ANY, ShapeContext.absent()));

        return hit.getType() == HitResult.Type.MISS;
    }

    private void onLookedAt(EndermanEntity enderman, ServerPlayerEntity player) {
        // TODO trigger attackAggressively when lookedAt for too long

        setScared(true);
        scaredTimer = SCARED_TICKS;

        BlockPos pos = findFleePos(enderman, player);

        if (pos == null) {
            attackAggressively(enderman, player);
            return;
        }

        if (!pos.equals(fleeTargetPos)) {
            playSound(SoundEvents.ENTITY_ENDERMAN_HURT, 2.5f, 1.4f);
        }

        if (DEBUG_TARGET_FLEE_POS) {
            args.debugController().exclusive("target_flee_pos", controller -> {
                var state = Blocks.CYAN_CONCRETE.getDefaultState();

                controller.displayMarker(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, state, 0x03b2fe, 0.5);
            });
        }

        if (!fleeToo(enderman, pos)) {
            attackAggressively(enderman, player);
        }
    }

    private void playSound(SoundEvent sound, float volume, float pitch) {
        EndermanEntity mob = mob();

        if (mob == null) return;

        mob.getWorld().playSound(null, mob.getX(), mob.getY(), mob.getZ(), sound, mob.getSoundCategory(), volume, pitch);
    }

    private boolean fleeToo(EndermanEntity mob, BlockPos pos) {
        EntityNavigation nav = mob.getNavigation();
        Path path = nav.findPathTo(pos, 0);

        if (path == null) {
            return false;
        }

        fleeTargetTimeout = 0;
        fleeTargetPos = pos;
        nav.startMovingAlong(path, 1);

        EntityAttributeInstance attr = mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);

        if (attr != null && !attr.hasModifier(FLEE_BONUS_ID)) {
            attr.addTemporaryModifier(new EntityAttributeModifier(FLEE_BONUS_ID, FLEE_SPEED_BONUS, EntityAttributeModifier.Operation.ADD_VALUE));
        }

        return true;
    }

    private void stopFleeing(EndermanEntity mob) {
        fleeTargetPos = null;

        if (DEBUG_TARGET_FLEE_POS) {
            args.debugController().exclusive("target_flee_pos", debugger -> {});
        }

        EntityAttributeInstance attr = mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);

        if (attr != null) {
            attr.removeModifier(FLEE_BONUS_ID);
        }
    }

    private void attackAggressively(EndermanEntity enderman, ServerPlayerEntity player) {
        // TODO implement
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

    private @Nullable BlockPos findFleePos(EndermanEntity mob, ServerPlayerEntity player) {
        Vec3d mobPos = mob.getPos();
        Vec3d playerPos = player.getPos();

        var entityNode = struct.nodeAt(mobPos);
        var playerNode = struct.nodeAt(playerPos);

        if (entityNode == null || playerNode == null) return null;

        final int maxChecks = 10;
        Queue<Node<Connector3, StructurePiece, OrientedStructurePiece>> queue = new LinkedList<>();
        Set<Node<Connector3, StructurePiece, OrientedStructurePiece>> known = new HashSet<>();

        if (entityNode == playerNode) {
            // only consider neighbours don't require the mob to go towards the player
            EntityNavigation nav = mob.getNavigation();
            BlockPos mobBlockPos = mob.getBlockPos();
            Vec3d mobToPlayerDir = playerPos.subtract(mobPos).withAxis(Direction.Axis.Y, 0).normalize();

            if (DEBUG_FLEE_PATHS) {
                args.debugController().displayArrow(mobPos.add(mobToPlayerDir.multiply(0.3)), mobToPlayerDir, 0.6, Blocks.BLUE_CONCRETE.getDefaultState());
            }

            for (Passage passage : struct.passagesOf(entityNode)) {
                BlockPos pos = passage.pos();
                Path path = nav.findPathTo(pos, 0);

                if (path == null) continue;

                Vec3d dir = startingDirection(mobBlockPos, path).withAxis(Direction.Axis.Y, 0).normalize();

                if (Math.abs(dir.length() - 1) > 1e-6) continue;

                if (DEBUG_FLEE_PATHS) {
                    args.debugController().displayArrow(mobPos.add(dir.multiply(0.3)), dir, 0.6, Blocks.LIME_CONCRETE.getDefaultState());
                }

                double angle = Math.acos(mobToPlayerDir.dotProduct(dir));

                System.out.println("angle to player: " + Math.toDegrees(angle));

                if (angle < Math.toRadians(FLEE_MIN_ANGLE_DEG)) continue;

                var neighbour = passage.other(entityNode);

                queue.offer(neighbour);
                known.add(neighbour);
            }
        } else {
            queue.offer(entityNode);
            known.add(entityNode);
        }

        LinkedHashSet<BlockPos> debugPositions = DEBUG_FLEE_POSITIONS ? new LinkedHashSet<>() : null;
        int i = 0;

        while (!queue.isEmpty() && i++ <= maxChecks) {
            var node = queue.poll();

            if (node == playerNode) continue;

            // this could be made optimized by either accepting the first position, or making this lazy
            var fleePos = findFleePositions(node, pos -> {
                if (isVisibleByAt(mob, player, pos.toBottomCenterPos())) {
                    return false;
                }

                if (DEBUG_FLEE_POSITIONS) {
                    debugPositions.add(pos);
                    return false;
                }

                return true;
            });

            if (fleePos != null) {
                return fleePos;
            }

            for (@Nullable var neighbour : node.neighbours()) {
                if (neighbour == null || !known.add(neighbour)) continue;

                queue.offer(neighbour);
            }
        }

        if (DEBUG_FLEE_POSITIONS && !debugPositions.isEmpty()) {
            args.debugController().exclusive("flee_positions", controller -> {
                var state = Blocks.MAGENTA_TERRACOTTA.getDefaultState();

                for (BlockPos pos : debugPositions) {
                    controller.displayMarker(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, state, 0xd808db);
                }
            });

            return debugPositions.getFirst();
        }

        return null;
    }

    private Vec3d startingDirection(BlockPos start, Path path) {
        int samples = Math.min(4, path.getLength() - 1);

        if (samples <= 0) {
            return Vec3d.ZERO;
        }

        final int sx = start.getX(), sy = start.getY(), sz = start.getZ();
        double x = 0, y = 0, z = 0;

        // first position is the start pos, don't count it as it will be zero
        for (int i = 1; i <= samples; i++) {
            PathNode node = path.getNode(i);
            BlockPos pos = node.getBlockPos();

            if (DEBUG_FLEE_PATHS) {
                args.debugController().displayMarker(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Blocks.BLACK_CONCRETE.getDefaultState(), 0);
            }

            x += (pos.getX() - sx);
            y += (pos.getY() - sy);
            z += (pos.getZ() - sz);
        }

        return new Vec3d(x / samples, y / samples, z / samples).normalize();
    }

    private @Nullable BlockPos findFleePositions(Node<Connector3, StructurePiece, OrientedStructurePiece> node, Predicate<BlockPos> positions) {
        OrientedStructurePiece oriented = node.oriented();

        if (oriented != null) {
            Vec3d spawn = oriented.spawn();

            if (spawn != null) {
                BlockPos pos = BlockPos.ofFloored(spawn);

                if (positions.test(pos)) {
                    return pos;
                }
            }
        }

        for (Passage passage : struct.passagesOf(node)) {
            BlockPos pos = passage.pos();

            if (positions.test(pos)) {
                return pos;
            }
        }

        return null;
    }

    public @Nullable BlockPos targetPos() {
        if (fleeTargetPos != null) {
            return fleeTargetPos;
        }

        EndermanEntity mob = mob();

        if (mob == null) {
            return null;
        }

        LivingEntity target = mob.getTarget();

        if (target == null) {
            return null;
        }

        return target.getBlockPos();
    }
}
