package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.core.Position;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.warden.Warden;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.core.mixin.EnderManAccessor;
import work.lclpnet.ap2.game.maze_scape.monster.EndermanData;
import work.lclpnet.ap2.game.maze_scape.monster.MonsterData;

import java.util.*;

import static java.lang.Double.isFinite;
import static java.lang.Double.isNaN;

public class MSTargetManager {

    private final MSStruct struct;
    private final Participants participants;
    private final Set<MonsterData<?>> monsters = new HashSet<>();

    public MSTargetManager(MSStruct struct, Participants participants) {
        this.struct = struct;
        this.participants = participants;
    }

    public void addMonster(MonsterData<?> monster) {
        monsters.add(monster);
    }

    public void update() {
        record Entry(double distance, MonsterData<?> monster, ServerPlayer player) {}

        // collect distances for from each monster to each player
        List<Entry> entries = new ArrayList<>(monsters.size() * participants.count());

        for (var monster : monsters) {
            Mob mob = monster.mob();

            if (mob == null) continue;

            for (ServerPlayer player : participants) {
                double distance = distanceBetween(player.position(), mob.position());

                if (!isNaN(distance) && isFinite(distance) && distance >= 0) {
                    entries.add(new Entry(distance, monster, player));
                }
            }
        }

        // sort by distance ascending
        entries.sort(Comparator.comparingDouble(Entry::distance));

        // assign player closest to each mob, exclusively
        Set<MonsterData<?>> assignedMonsters = new HashSet<>();
        Set<ServerPlayer> assignedPlayers = new HashSet<>();

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

    public void assignTarget(MonsterData<?> monster, ServerPlayer player) {
        Mob mob = monster.mob();

        if (mob == null) return;

        mob.setTarget(player);

        switch (mob) {
            case Warden warden -> warden.setAttackTarget(player);
            case EnderMan enderman when monster instanceof EndermanData data -> {
                AttributeInstance instance = enderman.getAttribute(Attributes.MOVEMENT_SPEED);

                if (instance != null) {
                    instance.removeModifier(Identifier.withDefaultNamespace("attacking"));
                }

                SynchedEntityData dataTracker = enderman.getEntityData();
                dataTracker.set(EnderManAccessor.DATA_CREEPY(), data.isScreaming());  // angry attribute differs from data.isAngry()

                dataTracker.set(EnderManAccessor.DATA_STARED_AT(), data.isAngry());
            }
            case Creaking creaking -> creaking.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, player);
            default -> {}
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
