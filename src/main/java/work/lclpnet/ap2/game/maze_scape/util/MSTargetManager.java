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
            if (assignedMonsters.contains(entry.mob) || (assignedPlayers.contains(entry.player))) continue;

            assignedMonsters.add(entry.mob);
            assignedPlayers.add(entry.player);

            assignTarget(entry.mob, entry.player);

            if (assignedMonsters.size() >= monsters.size()) break;
        }

        if (assignedMonsters.size() >= monsters.size()) return;

        // for every remaining monster, allow duplicate player assignment
        for (var entry : entries) {
            if (assignedMonsters.contains(entry.mob)) continue;

            assignedMonsters.add(entry.mob);

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
        // TODO cache estimated distance between to passages
        // TODO use real distance between passages rather than estimation
        return struct.findPath(from, to)
                .map(NavPath::length)
                .orElse(Double.POSITIVE_INFINITY);
    }
}
