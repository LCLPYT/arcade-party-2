package work.lclpnet.ap2.game.maze_scape.monster;

import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;
import org.joml.Vector4d;
import work.lclpnet.ap2.core.mixin.EndermanEntityAccessor;
import work.lclpnet.ap2.game.maze_scape.gen.Node;
import work.lclpnet.ap2.game.maze_scape.setup.Connector3;
import work.lclpnet.ap2.game.maze_scape.setup.OrientedStructurePiece;
import work.lclpnet.ap2.game.maze_scape.setup.StructurePiece;
import work.lclpnet.ap2.game.maze_scape.util.MSStruct;
import work.lclpnet.ap2.game.maze_scape.util.Passage;
import work.lclpnet.ap2.impl.util.math.MathUtil;

import java.util.*;
import java.util.function.Predicate;

import static java.lang.Math.abs;

public class EndermanData implements MonsterData {

    private static final int
            VISIBLE_CHECK_INTERVAL_TICKS = 10,
            SCARED_TICKS = 18;
    private static final double
            PLAYER_FOV = Math.toRadians(90),
            PLAYER_ASPECT_RATIO = 1920 / 1080.d;
    private static final boolean
            DEBUG_FLEE_POSITIONS = false,
            DEBUG_TARGET_FLEE_POS = true;

    private final MonsterArgs args;
    private final CommonData common;
    private final MSStruct struct;
    private int visibleCheckTimer = 0;
    private boolean scared = false, fleeing = false;
    private int scaredTimer = 0;
    private final Matrix4d mat = new Matrix4d();

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
        return isVisibleByAt(enderman, player, enderman.getPos());
    }

    private boolean isVisibleByAt(EndermanEntity enderman, ServerPlayerEntity player, Vec3d pos) {
        // check if the enderman is within the players (estimated) view frustum
        MathUtil.viewProjectionMatrix(player, PLAYER_FOV, PLAYER_ASPECT_RATIO, mat);

        Vec3d to = pos.add(0, enderman.getStandingEyeHeight(), 0);

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
        if (fleeing) return;  // TODO change

        setScared(true);
        scaredTimer = SCARED_TICKS;
        fleeing = true;

        BlockPos pos = findFleePos(enderman, player);

        if (pos == null) return;

        if (DEBUG_TARGET_FLEE_POS) {
            args.debugController().exclusive("target_flee_pos", controller -> {
                var state = Blocks.CYAN_CONCRETE.getDefaultState();

                controller.displayMarker(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, state, 0x03b2fe, 0.5);
            });
        }

        // TODO implement fleeing
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

    private @Nullable BlockPos findFleePos(EndermanEntity enderman, ServerPlayerEntity player) {
        var entityNode = struct.nodeAt(enderman.getPos());
        var playerNode = struct.nodeAt(player.getPos());

        if (entityNode == null || playerNode == null) return null;

        final int maxChecks = 10;
        Queue<Node<Connector3, StructurePiece, OrientedStructurePiece>> queue = new LinkedList<>();
        Set<Node<Connector3, StructurePiece, OrientedStructurePiece>> known = new HashSet<>();

        queue.offer(entityNode);
        known.add(entityNode);

        LinkedHashSet<BlockPos> debugPositions = DEBUG_FLEE_POSITIONS ? new LinkedHashSet<>() : null;

        while (!queue.isEmpty() && known.size() <= maxChecks) {
            var node = queue.poll();

            if (node == playerNode) continue;

            // this could be made optimized by either accepting the first position, or making this lazy
            var fleePos = findFleePositions(node, pos -> {
                if (isVisibleByAt(enderman, player, pos.toBottomCenterPos())) return false;

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
}
