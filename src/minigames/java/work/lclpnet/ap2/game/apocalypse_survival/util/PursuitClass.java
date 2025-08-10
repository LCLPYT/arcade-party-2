package work.lclpnet.ap2.game.apocalypse_survival.util;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.Participants;

import java.util.*;
import java.util.function.Supplier;

public class PursuitClass<T extends MobEntity> {

    private static final int INITIAL_FORCED_TARGET_SECONDS = 10;
    private final Participants participants;
    private final int capacityPerPlayer;
    private final Map<UUID, Pursuit<T>> pursuitMap;
    private final Set<T> mobs = new HashSet<>();
    private final Map<T, Pursuit<T>> pursuitMember = new HashMap<>();
    private final Object2IntMap<T> forcedTarget = new Object2IntOpenHashMap<>();

    public PursuitClass(Participants participants, int capacityPerPlayer) {
        this.participants = participants;
        this.capacityPerPlayer = capacityPerPlayer;
        this.pursuitMap = new HashMap<>(participants.count());

        for (ServerPlayerEntity player : participants) {
            UUID uuid = player.getUuid();

            Supplier<@Nullable LivingEntity> playerGetter = () -> participants.getParticipant(uuid).orElse(null);

            var pursuit = new Pursuit<T>(playerGetter, capacityPerPlayer);

            pursuitMap.put(uuid, pursuit);
        }
    }

    public void addMob(T mob) {
        mobs.add(mob);

        forceTargetInitially(mob);
    }

    public void removeMob(T mob) {
        mobs.remove(mob);
        stopPursuit(mob);
    }

    public void removeParticipant(ServerPlayerEntity player) {
        pursuitMap.remove(player.getUuid());
    }

    private void forceTargetInitially(T mob) {
        // if there are not enough mobs so that every player has maximum pursuers, force target initially
        if (mobs.size() >= participants.count() * capacityPerPlayer) return;

        // mobs will be forced to target the player with the least pursuers initially, so that mobs will spread evenly
        ServerPlayerEntity player = playerWithLeastPursuers().orElse(null);

        if (player == null) return;

        var pursuit = pursuitMap.get(player.getUuid());

        if (pursuit == null) return;

        // keep track of ticks remaining until normal targeting behaviour
        forcedTarget.put(mob, INITIAL_FORCED_TARGET_SECONDS);

        changePursuit(mob, player, pursuit);
    }

    public void update() {
        // set all pursuit instances to need an update, because mobs will have moved since the last update
        for (var pursuit : pursuitMap.values()) {
            pursuit.setNeedsUpdate();
        }

        // update each mobs target
        nextMob: for (T mob : mobs) {
            // skip mob if it has a forced target
            // forcedTarget will end either after the configured duration or if another mob replaces it as pursuer
            if (forcedTarget.containsKey(mob) && shouldKeepForcedTarget(mob)) continue;

            for (ServerPlayerEntity player : playersByDistance(mob)) {
                var pursuit = pursuitMap.get(player.getUuid());

                if (pursuit == null) continue;

                if (pursuit.hasCapacity()) {
                    changePursuit(mob, player, pursuit);
                    continue nextMob;
                }

                // check if the current mob can replace the most distant pursuer because it is closer to the player
                T mostDistant = pursuit.getMostDistantPursuer();

                if (mostDistant == null || mob.squaredDistanceTo(player) >= mostDistant.squaredDistanceTo(player)) {
                    continue;
                }

                // current mob is closer, replace old pursuer.
                // the old pursuer will only be updated in the next iteration.
                // this could theoretically be changed to another pass in this update
                stopPursuit(mostDistant);
                changePursuit(mob, player, pursuit);
            }

            // didn't find a player to pursuit, start roaming (RoamGoal will pick up)
        }
    }

    private boolean shouldKeepForcedTarget(T mob) {
        int remaining = forcedTarget.getInt(mob);

        if (remaining <= 1) {
            forcedTarget.removeInt(mob);
            return false;
        }

        forcedTarget.put(mob, remaining - 1);
        return true;
    }

    private Iterable<ServerPlayerEntity> playersByDistance(T mob) {
        return () -> participants.stream()
                .sorted(Comparator.comparingDouble(mob::squaredDistanceTo))
                .iterator();
    }

    private void changePursuit(T mob, LivingEntity target, Pursuit<T> pursuit) {
        var oldPursuit = pursuitMember.put(mob, pursuit);

        if (oldPursuit != null) {
            oldPursuit.removePursuer(mob);
        }

        if (pursuit.addPursuer(mob)) {
            mob.setTarget(target);
        } else {
            stopPursuit(mob);
        }
    }

    private void stopPursuit(T mob) {
        var pursuit = pursuitMember.remove(mob);

        if (pursuit != null) {
            pursuit.removePursuer(mob);
        }

        mob.setTarget(null);

        forcedTarget.removeInt(mob);
    }

    private Optional<ServerPlayerEntity> playerWithLeastPursuers() {
        return participants.stream().min(Comparator.comparingInt(player -> {
            var pursuit = pursuitMap.get(player.getUuid());

            if (pursuit == null) {
                // player not registered in pursuit map, should not happen
                return Integer.MAX_VALUE;
            }

            return pursuit.getPursuerCount();
        }));
    }
}
