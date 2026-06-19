package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.game.maze_scape.gen.Node;
import work.lclpnet.ap2.game.maze_scape.setup.Connector3;
import work.lclpnet.ap2.game.maze_scape.setup.MSDebugController;
import work.lclpnet.ap2.game.maze_scape.setup.OrientedStructurePiece;
import work.lclpnet.ap2.game.maze_scape.setup.StructurePiece;
import work.lclpnet.ap2.game.player.Participants;
import work.lclpnet.ap2.impl.util.VisibilityChecker;

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

    public Optional<Path> findEscapePath(EnderMan mob) {
        Vec3 mobPos = mob.position();
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

            Vec3 spawn = oriented.spawn();

            if (spawn != null) {
                Path path = escapePath(spawn, mob);

                if (path != null && !leadingToAny(mobPos, path)) {
                    paths.add(path);
                }
            }

            for (Passage passage : struct.passagesOf(node)) {
                Path path = escapePath(Vec3.atBottomCenterOf(passage.pos()), mob);

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
            debugController.parent().renderer().ifPresent(renderer -> debugController.parent().exclusive("flee_positions", _ -> paths.stream()
                    .map(Path::getTarget)
                    .map(Vec3::atBottomCenterOf)
                    .forEach(pos -> renderer.marker(pos, Blocks.DYED_TERRACOTTA.magenta().defaultBlockState(), 0xd808db))));
        }

        return paths.stream().min(Comparator.comparingInt(Path::getNodeCount));
    }

    private @Nullable Path escapePath(Vec3 pos, EnderMan mob) {
        if (visibilityChecker.isAnyoneLookingAt(mob, pos, participants)) {
            return null;
        }

        return mob.getNavigation().createPath(BlockPos.containing(pos), 0);
    }

    private boolean leadingToAny(Vec3 startPos, Path path) {
        Vec3 startingDir = startingDirection(BlockPos.containing(startPos), path).with(Direction.Axis.Y, 0).normalize();

        if (!isUnit(startingDir)) return false;

        final double maxDistSq = 32 * 32;

        for (ServerPlayer player : participants) {
            if (player.distanceToSqr(startPos) > maxDistSq) continue;

            Vec3 playerDir = player.position().subtract(startPos).with(Direction.Axis.Y, 0).normalize();

            if (!isUnit(playerDir)) continue;

            double angle = acos(playerDir.dot(startingDir));

            if (angle < toRadians(FLEE_MIN_ANGLE_DEG)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isUnit(Vec3 playerDir) {
        return abs(playerDir.lengthSqr() - 1) < 1e-4;
    }

    private Vec3 startingDirection(BlockPos start, Path path) {
        int samples = min(4, path.getNodeCount() - 1);

        if (samples <= 0) {
            return Vec3.ZERO;
        }

        final int sx = start.getX(), sy = start.getY(), sz = start.getZ();
        double x = 0, y = 0, z = 0;

        // first position is the start pos, don't count it as it will be zero
        for (int i = 1; i <= samples; i++) {
            var node = path.getNode(i);
            BlockPos pos = node.asBlockPos();

            if (DEBUG_FLEE_PATHS) {
                debugController.parent().renderer().ifPresent(renderer ->
                        renderer.marker(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Blocks.CONCRETE.black().defaultBlockState(), 0));
            }

            x += (pos.getX() - sx);
            y += (pos.getY() - sy);
            z += (pos.getZ() - sz);
        }

        return new Vec3(x / samples, y / samples, z / samples).normalize();
    }
}
