package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.block.Blocks;
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

    public Optional<Path> findEscapePath(EndermanEntity mob) {
        Vec3d mobPos = mob.getPos();
        var entityNode = struct.nodeAt(mobPos);

        if (entityNode == null) return Optional.empty();

        Queue<Node<Connector3, StructurePiece, OrientedStructurePiece>> queue = new LinkedList<>();
        Set<Node<Connector3, StructurePiece, OrientedStructurePiece>> known = new HashSet<>();

        queue.offer(entityNode);
        known.add(entityNode);

        List<Path> paths = new ArrayList<>();
        final int maxChecks = 6;
        int check = 0;

        while (!queue.isEmpty() && check++ < maxChecks) {
            var node = queue.poll();

            OrientedStructurePiece oriented = node.oriented();

            if (oriented == null) continue;

            Vec3d spawn = oriented.spawn();

            if (spawn != null) {
                Path path = escapePath(spawn, mob);

                if (path != null && !leadingToAny(mobPos, path)) {
                    paths.add(path);
                }
            }

            for (Passage passage : struct.passagesOf(node)) {
                Path path = escapePath(passage.pos().toBottomCenterPos(), mob);

                if (path != null) {
                    if (leadingToAny(mobPos, path)) continue;

                    paths.add(path);
                }

                var next = passage.other(node);

                if (next != null && known.add(next)) {
                    queue.offer(next);
                }
            }
        }

        if (DEBUG_FLEE_POSITIONS) {
            debugController.exclusive("flee_positions", controller -> paths.stream()
                    .map(Path::getTarget)
                    .map(BlockPos::toBottomCenterPos)
                    .forEach(pos -> controller.displayMarker(pos, Blocks.MAGENTA_TERRACOTTA.getDefaultState(), 0xd808db)));
        }

        return paths.stream().min(Comparator.comparingInt(Path::getLength));
    }

    private @Nullable Path escapePath(Vec3d pos, EndermanEntity mob) {
        if (visibilityChecker.isAnyoneLookingAt(mob, pos, participants)) {
            return null;
        }

        return mob.getNavigation().findPathTo(BlockPos.ofFloored(pos), 0);
    }

    private boolean leadingToAny(Vec3d startPos, Path path) {
        Vec3d startingDir = startingDirection(BlockPos.ofFloored(startPos), path).withAxis(Direction.Axis.Y, 0).normalize();

        if (!isUnit(startingDir)) return false;

        final double maxDistSq = 32 * 32;

        for (ServerPlayerEntity player : participants) {
            if (player.squaredDistanceTo(startPos) > maxDistSq) continue;

            Vec3d playerDir = player.getPos().subtract(startPos).withAxis(Direction.Axis.Y, 0).normalize();

            if (!isUnit(playerDir)) continue;

            double angle = acos(playerDir.dotProduct(startingDir));

            if (angle < toRadians(FLEE_MIN_ANGLE_DEG)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isUnit(Vec3d playerDir) {
        return abs(playerDir.lengthSquared() - 1) < 1e-4;
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
}
