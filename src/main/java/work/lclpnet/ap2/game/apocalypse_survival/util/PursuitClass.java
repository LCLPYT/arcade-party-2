package work.lclpnet.ap2.game.apocalypse_survival.util;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.Participants;

import java.util.*;
import java.util.function.Supplier;

public class PursuitClass<T extends MobEntity> {

    private final Participants participants;
    private final Map<UUID, Pursuit<T>> pursuitMap;
    private final Set<T> mobs = new HashSet<>();
    private final Map<T, Pursuit<T>> pursuitMember = new HashMap<>();
    private final TargetManager debug$targetManager;

    public PursuitClass(Participants participants, int capacityPerPlayer, TargetManager debug$targetManager) {
        this.participants = participants;
        this.pursuitMap = new HashMap<>(participants.count());
        this.debug$targetManager = debug$targetManager;

        for (ServerPlayerEntity player : participants) {
            UUID uuid = player.getUuid();

            Supplier<@Nullable LivingEntity> playerGetter = () -> participants.getParticipant(uuid).orElse(null);

            var pursuit = new Pursuit<T>(playerGetter, capacityPerPlayer);

            pursuitMap.put(uuid, pursuit);
        }
    }

    public void addMob(T mob) {
        mobs.add(mob);
    }

    public void removeMob(T mob) {
        mobs.remove(mob);
        stopPursuit(mob);
    }

    public void removeParticipant(ServerPlayerEntity player) {
        pursuitMap.remove(player.getUuid());
    }

    public void update() {
        // set all pursuit instances to need an update, because mobs will have moved since the last update
        for (var pursuit : pursuitMap.values()) {
            pursuit.setNeedsUpdate();
        }

        // update each mobs target
        nextMob: for (T mob : mobs) {
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

            debug$targetManager.debug$getScoreboardManager().joinTeam(mob, debug$targetManager.debug$getPursuitTeam());
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

        debug$targetManager.debug$getScoreboardManager().leaveTeam(mob, debug$targetManager.debug$getPursuitTeam());
    }
}
