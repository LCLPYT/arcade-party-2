package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Position;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.game.maze_scape.gen.Node;
import work.lclpnet.ap2.game.maze_scape.setup.Connector3;
import work.lclpnet.ap2.game.maze_scape.setup.OrientedStructurePiece;
import work.lclpnet.ap2.game.maze_scape.setup.StructurePiece;

import java.util.*;

import static java.lang.Math.sqrt;

public class MSTargetManager {

    private final MSStruct struct;
    private final Participants participants;
    private final ServerWorld world;
    private final Map<UUID, UUID> playerTargetedBy = new HashMap<>();
    private final Map<UUID, TargetTracker<GraphPos>> trackers = new HashMap<>();

    public MSTargetManager(MSStruct struct, Participants participants, ServerWorld world) {
        this.struct = struct;
        this.participants = participants;
        this.world = world;
    }

    public void updateTarget(MobEntity mob) {
        var tracker = trackers.computeIfAbsent(mob.getUuid(), uuid -> createTracker(mob));

        tracker.update(participants.getAsSet());

        $debugTargets(mob, tracker);
    }

    private void $debugTargets(MobEntity mob, TargetTracker<GraphPos> tracker) {
        System.out.println("TARGETS LIST for " + mob.getName().getString());

        var ref = struct.nodeAt(mob.getPos());

        if (ref == null) return;

        var refPos = new GraphPos(mob.getUuid(), mob.getPos(), ref);
        int i = 1;

        for (var target : tracker.targets()) {
            var player = participants.getParticipant(target.uuid()).orElse(null);

            if (player == null) continue;

            double dist = distanceBetween(target, refPos);
            System.out.printf("#%d\t%s\t %f\n", i++, player.getNameForScoreboard(), dist);
        }
    }

    public void assignTarget(MobEntity mob, ServerPlayerEntity player) {
        mob.setTarget(player);

        playerTargetedBy.put(player.getUuid(), mob.getUuid());

        if (mob instanceof WardenEntity warden) {
            warden.updateAttackTarget(player);
        }
    }

    private @NotNull TargetTracker<GraphPos> createTracker(MobEntity _mob) {
        return new TargetTracker<>(_mob, world, player -> {
            Vec3d pos = player.getPos();
            var node = struct.nodeAt(pos);

            if (node == null) {
                return null;
            }

            return new GraphPos(player.getUuid(), pos, node);
        }, mob -> {
            Vec3d mobPos = mob.getPos();
            var ref = struct.nodeAt(mobPos);

            if (ref == null) {
                return null;
            }

            var refPos = new GraphPos(mob.getUuid(), mobPos, ref);

            return Comparator.comparingDouble(from -> distanceBetween(from, refPos));
        });
    }

    private double distanceBetween(GraphPos from, GraphPos to) {
        var passageFrom = struct.nearestPassageTo(from.exact());
        var passageTo = struct.nearestPassageTo(to.exact());

        if (passageFrom == null || passageTo == null) {
            return Double.POSITIVE_INFINITY;
        }

        // TODO cache estimated distance between to passages
        // TODO use real distance between passages rather than estimation
        List<Passage> path = struct.passagePathFinder().findPath(passageFrom, passageTo);

        if (path.isEmpty()) {
            // no path found
            return Double.POSITIVE_INFINITY;
        }

        double distance = 0;
        var last = path.getFirst();

        // sum estimated distance between passages
        for (int i = 1, len = path.size(); i < len; i++) {
            var next = path.get(i);
            distance += sqrt(last.pos().getSquaredDistance(next.pos()));
            last = next;
        }

        // add estimated distance between exact from / to position and their respective passage
        distance += sqrt(passageFrom.pos().getSquaredDistance(from.exact()));
        distance += sqrt(passageTo.pos().getSquaredDistance(to.exact()));

        return distance;
    }

    private record GraphPos(
            @NotNull UUID uuid,
            Position exact,
            Node<Connector3, StructurePiece, OrientedStructurePiece> node
    ) implements TargetTracker.Key {}
}
