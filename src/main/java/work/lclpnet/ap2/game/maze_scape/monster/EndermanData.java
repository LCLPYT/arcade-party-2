package work.lclpnet.ap2.game.maze_scape.monster;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
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
            FLEE_SPEED_BONUS = 0.05,
            ANGER_SPEED_BONUS = 0.1,
            FLEE_MIN_ANGLE_DEG = 65.0,
            LOOK_AT_ANGER_AMOUNT = 25.0,
            ANGER_TRIGGER_THRESHOLD = 350.0,
            ANGER_DECAY_PER_SECOND = 3.5,
            ANGER_TRIGGER_BONUS = ANGER_DECAY_PER_SECOND * 12.0;
    private static final boolean
            DEBUG_FLEE_POSITIONS = false,
            DEBUG_TARGET_FLEE_POS = true,
            DEBUG_FLEE_PATHS = false;
    private static final Identifier
            FLEE_BONUS_ID = ArcadeParty.identifier("flee_bonus"),
            ANGER_BONUS_ID = ArcadeParty.identifier("anger_bonus");

    private final MonsterArgs args;
    private final CommonData common;
    private final MSStruct struct;
    private int timer = 0;
    private boolean screaming = false;
    private int scaredTimer = 0;
    private final Matrix4d viewProjMat = new Matrix4d();
    private @Nullable BlockPos fleeTargetPos = null;
    private int fleeTargetTimeout = 0;
    private double anger = 0;
    private @Nullable UUID angerTarget = null;

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

        if (timer % VISIBLE_CHECK_INTERVAL_TICKS == 0) {
            checkVisible();
        }

        if (scaredTimer > 0 && --scaredTimer == 0) {
            setScreaming(false);
        }

        if (fleeTargetTimeout > 0) {
            if (--fleeTargetTimeout == 0) {
                stopFleeing(mob);
            }
        } else if (fleeTargetPos != null && fleeTargetPos.getSquaredDistance(mob.getPos()) <= 1.44)  {
            fleeTargetTimeout = FLEE_TIMEOUT_TICKS;
        }

        if (angerTarget != null) {
            ServerPlayerEntity player = args.manager().participants().getParticipant(angerTarget)
                    .filter(p -> p.isAlive() && !p.isSpectator())
                    .orElse(null);

            if (player == null) {
                anger = 0;
                angerTarget = null;
            }
        }

        if (timer % 20 == 0) {
            setAnger(Math.max(0.0, anger - ANGER_DECAY_PER_SECOND), null);
        }

        timer++;
    }

    @Override
    public void onKillAcquired() {
        common.onKillAcquired();
        setAnger(0, null);

        EndermanEntity mob = mob();

        if (mob != null) {
            stopFleeing(mob);
        }
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
        MathUtil.viewProjectionMatrix(player, PLAYER_FOV, PLAYER_ASPECT_RATIO, viewProjMat);

        if (isVisibleBy(enderman, player)) {
            onLookedAt(enderman, player);
        }
    }

    private boolean isVisibleBy(EndermanEntity enderman, ServerPlayerEntity player) {
        return isVisibleByAt(enderman, player, enderman.getPos(), -0.1);
    }

    private boolean isVisibleByAt(EndermanEntity enderman, ServerPlayerEntity player, Vec3d pos, double margin) {
        // check if the enderman is within the players (estimated) view frustum
        Box bounds = enderman.getDimensions(enderman.getPose()).getBoxAt(pos).expand(margin);  // add some margin
        Vector4d ndc = new Vector4d();
        Vec3d playerEyePos = player.getEyePos();

        for (Vec3d corner : MathUtil.corners(bounds)) {
            ndc.set(corner.x, corner.y, corner.z);
            viewProjMat.transform(ndc);
            ndc.div(ndc.w);

            if (abs(ndc.x) > 1.d || abs(ndc.y) > 1.d || abs(ndc.z) > 1.d) continue;

            // within view frustum, check for occlusion
            if (!occluded(playerEyePos, corner, common.manager().world())) {
                return true;
            }
        }

        return false;
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

    private void onLookedAt(EndermanEntity mob, ServerPlayerEntity player) {
        if (isAngry()) return;

        setScreaming(true);
        scaredTimer = SCARED_TICKS;

        BlockPos pos = findFleePos(mob, player);

        if (pos == null) {
            angerFully(player);
            return;
        }

        if (!pos.equals(fleeTargetPos)) {
            playSoundFar(player, mob, SoundEvents.ENTITY_ENDERMAN_HURT, 0.5f, 1.4f);
        }

        if (DEBUG_TARGET_FLEE_POS) {
            args.debugController().exclusive("target_flee_pos", controller -> controller.displayMarker(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Blocks.CYAN_CONCRETE.getDefaultState(), 0x03b2fe, 0.5));
        }

        if (fleeToo(mob, pos)) {
            setAnger(anger + LOOK_AT_ANGER_AMOUNT, player);
        } else {
            angerFully(player);
        }
    }

    private void setAnger(double amount, @Nullable ServerPlayerEntity player) {
        boolean wasAngry = isAngry();

        anger = amount;

        if (anger >= ANGER_TRIGGER_THRESHOLD) {
            if (player != null) {
                angerTarget = player.getUuid();
            }
        } else {
            angerTarget = null;
        }

        boolean angry = isAngry();

        if (wasAngry == angry) return;

        // state change
        setScreaming(angry);

        if (angry) {
            anger += ANGER_TRIGGER_BONUS;
            scaredTimer = 0;
        }

        EndermanEntity mob = mob();

        if (mob == null) return;

        stopFleeing(mob);

        mob.setSilent(!angry);
        mob.getDataTracker().set(EndermanEntityAccessor.PROVOKED(), angry);

        EntityAttributeInstance attr = mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);

        if (attr != null) {
            if (angry) {
                if (!attr.hasModifier(ANGER_BONUS_ID)) {
                    attr.addTemporaryModifier(new EntityAttributeModifier(ANGER_BONUS_ID, ANGER_SPEED_BONUS, EntityAttributeModifier.Operation.ADD_VALUE));
                }
            } else {
                attr.removeModifier(ANGER_BONUS_ID);
            }
        }

        if (angry && player != null) {
            playSoundFar(player, mob, SoundEvents.ENTITY_ENDERMAN_SCREAM, 1f, 1f);
        }
    }

    private void playSoundFar(@NotNull ServerPlayerEntity player, EndermanEntity mob, SoundEvent sound, float volume, float pitch) {
        World world = mob.getWorld();

        if (player.squaredDistanceTo(mob) >= 256) {
            world.playSound(player, mob.getBlockPos(), sound, mob.getSoundCategory(), volume, pitch);
            player.playSoundToPlayer(sound, mob.getSoundCategory(), volume, pitch);
        } else {
            world.playSound(null, mob.getBlockPos(), sound, mob.getSoundCategory(), volume, pitch);
        }
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
        fleeTargetTimeout = 0;

        if (DEBUG_TARGET_FLEE_POS) {
            args.debugController().exclusive("target_flee_pos", debugger -> {});
        }

        EntityAttributeInstance attr = mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);

        if (attr != null) {
            attr.removeModifier(FLEE_BONUS_ID);
        }
    }

    private void angerFully(ServerPlayerEntity player) {
        setAnger(ANGER_TRIGGER_THRESHOLD, player);
    }

    public boolean isScreaming() {
        return screaming;
    }

    private void setScreaming(boolean screaming) {
        this.screaming = screaming;

        EndermanEntity mob = mob();

        if (mob != null) {
            mob.getDataTracker().set(EndermanEntityAccessor.ANGRY(), screaming);
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

                if (abs(dir.length() - 1) > 1e-6) continue;

                if (DEBUG_FLEE_PATHS) {
                    args.debugController().displayArrow(mobPos.add(dir.multiply(0.3)), dir, 0.6, Blocks.LIME_CONCRETE.getDefaultState());
                }

                double angle = Math.acos(mobToPlayerDir.dotProduct(dir));

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
                if (isVisibleByAt(mob, player, pos.toBottomCenterPos(), 0.2)) {
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
        if (angerTarget != null) {
            BlockPos angerTargetPos = args.manager().participants().getParticipant(angerTarget)
                    .map(Entity::getBlockPos)
                    .orElse(null);

            if (angerTargetPos != null) {
                return angerTargetPos;
            }
        }

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

    public boolean isAngry() {
        return anger >= ANGER_TRIGGER_THRESHOLD;
    }

    public boolean isFleeing() {
        return fleeTargetPos != null && angerTarget == null;
    }
}
