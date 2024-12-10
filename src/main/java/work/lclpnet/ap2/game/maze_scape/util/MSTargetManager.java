package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Position;
import work.lclpnet.ap2.api.base.Participants;

import java.util.*;

import static java.lang.Double.isFinite;
import static java.lang.Double.isNaN;
import static java.lang.Math.sqrt;

public class MSTargetManager {

    private final MSStruct struct;
    private final Participants participants;
    private final ServerWorld world;
    private final Set<UUID> monsters = new HashSet<>();

    public MSTargetManager(MSStruct struct, Participants participants, ServerWorld world) {
        this.struct = struct;
        this.participants = participants;
        this.world = world;
    }

    public void addMonster(MobEntity mob) {
        monsters.add(mob.getUuid());
    }

    public void update() {
        record Entry(double distance, MobEntity mob, ServerPlayerEntity player) {}

        // collect distances for from each monster to each player
        List<Entry> entries = new ArrayList<>(monsters.size() * participants.count());

        for (UUID monsterId : monsters) {
            if (!(world.getEntity(monsterId) instanceof MobEntity mob)) continue;

            for (ServerPlayerEntity player : participants) {
                double distance = distanceBetween(player.getPos(), mob.getPos());

                if (!isNaN(distance) && isFinite(distance) && distance >= 0) {
                    entries.add(new Entry(distance, mob, player));
                }
            }
        }

        // sort by distance ascending
        entries.sort(Comparator.comparingDouble(Entry::distance));

        // assign player closest to each mob, exclusively
        Set<MobEntity> assignedMonsters = new HashSet<>();
        Set<ServerPlayerEntity> assignedPlayers = new HashSet<>();

        for (var entry : entries) {
            if (assignedMonsters.contains(entry.mob) || assignedPlayers.contains(entry.player)) continue;

            assignedMonsters.add(entry.mob);
            assignedPlayers.add(entry.player);

            System.out.printf("Assign %s to %s\n", entry.player.getNameForScoreboard(), entry.mob.getName().getString());

            assignTarget(entry.mob, entry.player);
        }
    }

    public void assignTarget(MobEntity mob, ServerPlayerEntity player) {
        mob.setTarget(player);

        if (mob instanceof WardenEntity warden) {
            warden.updateAttackTarget(player);
        }
    }

    private double distanceBetween(Position from, Position to) {
        var nodeFrom = struct.nodeAt(from);
        var nodeTo = struct.nodeAt(to);

        if (nodeFrom == null || nodeTo == null) {
            return Double.POSITIVE_INFINITY;
        }

        if (nodeTo == nodeFrom) {
            double dx = to.getX() - from.getX();
            double dy = to.getY() - from.getY();
            double dz = to.getZ() - from.getZ();

            return sqrt(dx * dx + dy * dy + dz * dz);
        }

        var passageFrom = struct.nearestPassageTo(from);
        var passageTo = struct.nearestPassageTo(to);

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
        distance += sqrt(passageFrom.pos().getSquaredDistance(from));
        distance += sqrt(passageTo.pos().getSquaredDistance(to));

        return distance;
    }
}
