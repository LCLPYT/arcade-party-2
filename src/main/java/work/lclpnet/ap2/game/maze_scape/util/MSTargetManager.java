package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Position;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.core.mixin.EndermanEntityAccessor;
import work.lclpnet.ap2.game.maze_scape.monster.EndermanData;
import work.lclpnet.ap2.game.maze_scape.monster.MonsterData;

import java.util.*;

import static java.lang.Double.isFinite;
import static java.lang.Double.isNaN;

public class MSTargetManager {

    private final MSStruct struct;
    private final Participants participants;
    private final Set<MonsterData> monsters = new HashSet<>();

    public MSTargetManager(MSStruct struct, Participants participants) {
        this.struct = struct;
        this.participants = participants;
    }

    public void addMonster(MonsterData monster) {
        monsters.add(monster);
    }

    public void update() {
        record Entry(double distance, MonsterData monster, ServerPlayerEntity player) {}

        // collect distances for from each monster to each player
        List<Entry> entries = new ArrayList<>(monsters.size() * participants.count());

        for (MonsterData monster : monsters) {
            MobEntity mob = monster.mob();

            if (mob == null) continue;

            for (ServerPlayerEntity player : participants) {
                double distance = distanceBetween(player.getPos(), mob.getPos());

                if (!isNaN(distance) && isFinite(distance) && distance >= 0) {
                    entries.add(new Entry(distance, monster, player));
                }
            }
        }

        // sort by distance ascending
        entries.sort(Comparator.comparingDouble(Entry::distance));

        // assign player closest to each mob, exclusively
        Set<MonsterData> assignedMonsters = new HashSet<>();
        Set<ServerPlayerEntity> assignedPlayers = new HashSet<>();

        for (var entry : entries) {
            if (assignedMonsters.contains(entry.monster) || (assignedPlayers.contains(entry.player))) continue;

            assignedMonsters.add(entry.monster);
            assignedPlayers.add(entry.player);

            assignTarget(entry.monster, entry.player);

            if (assignedMonsters.size() >= monsters.size()) break;
        }

        if (assignedMonsters.size() >= monsters.size()) return;

        // for every remaining monster, allow duplicate player assignment
        for (var entry : entries) {
            if (assignedMonsters.contains(entry.monster)) continue;

            assignedMonsters.add(entry.monster);

            assignTarget(entry.monster, entry.player);
        }
    }

    public void assignTarget(MonsterData monster, ServerPlayerEntity player) {
        MobEntity mob = monster.mob();

        if (mob == null) return;

        mob.setTarget(player);

        if (mob instanceof WardenEntity warden) {
            warden.updateAttackTarget(player);
        }

        if (mob instanceof EndermanEntity enderman && monster instanceof EndermanData data) {
            EntityAttributeInstance instance = enderman.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);

            if (instance != null) {
                instance.removeModifier(Identifier.ofVanilla("attacking"));
            }

            DataTracker dataTracker = enderman.getDataTracker();
            dataTracker.set(EndermanEntityAccessor.ANGRY(), data.isScreaming());  // angry attribute differs from data.isAngry()
            dataTracker.set(EndermanEntityAccessor.PROVOKED(), data.isAngry());
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
