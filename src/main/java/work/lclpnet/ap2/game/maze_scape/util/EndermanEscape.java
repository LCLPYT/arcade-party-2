package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.block.Blocks;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.game.maze_scape.gen.Node;
import work.lclpnet.ap2.game.maze_scape.setup.Connector3;
import work.lclpnet.ap2.game.maze_scape.setup.MSDebugController;
import work.lclpnet.ap2.game.maze_scape.setup.OrientedStructurePiece;
import work.lclpnet.ap2.game.maze_scape.setup.StructurePiece;

import java.util.*;
import java.util.function.Predicate;

import static java.lang.Math.*;

public class EndermanEscape {

    private static final boolean
            DEBUG_FLEE_POSITIONS = false,
            DEBUG_FLEE_PATHS = false;
    private static final double
            FLEE_MIN_ANGLE_DEG = 65.0;

    private final MSStruct struct;
    private final VisibilityChecker visibilityChecker;
    private final Participants participants;
    private final MSDebugController debugController;

    public EndermanEscape(MSStruct struct, VisibilityChecker visibilityChecker, Participants participants, MSDebugController debugController) {
        this.struct = struct;
        this.visibilityChecker = visibilityChecker;
        this.participants = participants;
        this.debugController = debugController;
    }

    public @Nullable BlockPos findFleePos(EndermanEntity mob, ServerPlayerEntity player) {
        Vec3d mobPos = mob.getPos();
        Vec3d playerPos = player.getPos();

        var entityNode = struct.nodeAt(mobPos);
        var playerNode = struct.nodeAt(playerPos);

        if (entityNode == null || playerNode == null) return null;

        final int maxChecks = 10;
        Queue<Node<Connector3, StructurePiece, OrientedStructurePiece>> queue = new LinkedList<>();
        Set<Node<Connector3, StructurePiece, OrientedStructurePiece>> known = new HashSet<>();

        // only consider neighbours don't require the mob to go towards the player
        EntityNavigation nav = mob.getNavigation();
        BlockPos mobBlockPos = mob.getBlockPos();
        Vec3d mobToPlayerDir = playerPos.subtract(mobPos).withAxis(Direction.Axis.Y, 0).normalize();

        if (DEBUG_FLEE_PATHS) {
            debugController.displayArrow(mobPos.add(mobToPlayerDir.multiply(0.3)), mobToPlayerDir, 0.6, Blocks.BLUE_CONCRETE.getDefaultState());
        }

        for (Passage passage : struct.passagesOf(entityNode)) {
            BlockPos pos = passage.pos();
            Path path = nav.findPathTo(pos, 0);

            if (path == null) continue;

            Vec3d dir = startingDirection(mobBlockPos, path).withAxis(Direction.Axis.Y, 0).normalize();

            if (abs(dir.length() - 1) > 1e-6) continue;

            if (DEBUG_FLEE_PATHS) {
                debugController.displayArrow(mobPos.add(dir.multiply(0.3)), dir, 0.6, Blocks.LIME_CONCRETE.getDefaultState());
            }

            double angle = acos(mobToPlayerDir.dotProduct(dir));

            if (angle < toRadians(FLEE_MIN_ANGLE_DEG)) continue;

            var neighbour = passage.other(entityNode);

            queue.offer(neighbour);
            known.add(neighbour);
        }

        LinkedHashSet<BlockPos> debugPositions = DEBUG_FLEE_POSITIONS ? new LinkedHashSet<>() : null;
        int i = 0;

        while (!queue.isEmpty() && i++ <= maxChecks) {
            var node = queue.poll();

            if (node == playerNode) continue;

            var fleePos = findFleePositions(node, pos -> {
                // check if any player would see the entity at pos
                if (visibilityChecker.isAnyoneLookingAt(mob, pos.toCenterPos(), participants)) return false;

                if (DEBUG_FLEE_POSITIONS) {
                    debugPositions.add(pos);
                    return false;  // debug should find all positions
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

        if (DEBUG_FLEE_POSITIONS) {
            debugController.exclusive("flee_positions", controller -> {
                var state = Blocks.MAGENTA_TERRACOTTA.getDefaultState();

                for (BlockPos pos : debugPositions) {
                    controller.displayMarker(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, state, 0xd808db);
                }
            });

            return debugPositions.isEmpty() ? null : debugPositions.getFirst();  // pick first to get same result as without debug
        }

        return null;
    }

    private Vec3d startingDirection(BlockPos start, Path path) {
        int samples = min(4, path.getLength() - 1);

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
                debugController.displayMarker(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Blocks.BLACK_CONCRETE.getDefaultState(), 0);
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
}
