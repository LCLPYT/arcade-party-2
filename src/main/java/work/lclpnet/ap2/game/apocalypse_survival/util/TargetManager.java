package work.lclpnet.ap2.game.apocalypse_survival.util;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.*;
import java.util.stream.Stream;

public class TargetManager {

    private static final int PURSUERS_PER_PLAYER = 20;
    private final Participants participants;
    private final CustomScoreboardManager scoreboardManager;
    private final Team targetTeam, guardTeam;
    private final SetMultimap<UUID, MobEntity> pursuit;
    private final Map<MobEntity, UUID> assigned = new HashMap<>();
    private final PriorityQueue<UUID> leastPursuers;
    private final Set<MobEntity> mobs = new HashSet<>();
    private final Set<MobEntity> unassigned = new HashSet<>();
    private final MobDensityManager densityManager;
    /** Used to store new pursuers in each update iteration. */
    private final Set<MobEntity> newPursuers = new HashSet<>();

    public TargetManager(Participants participants, GameMap map, CustomScoreboardManager scoreboardManager, Team targetTeam, Team guardTeam) {
        this.participants = participants;
        this.scoreboardManager = scoreboardManager;
        this.targetTeam = targetTeam;
        this.guardTeam = guardTeam;

        pursuit = HashMultimap.create(participants.count(), PURSUERS_PER_PLAYER);
        leastPursuers = new PriorityQueue<>(participants.count(), Comparator.comparingInt(uuid -> pursuit.get(uuid).size()));

        for (ServerPlayerEntity player : participants) {
            leastPursuers.add(player.getUuid());
        }

        densityManager = new MobDensityManager(map);
    }

    public void addMob(MobEntity mob) {
        synchronized (this) {
            mobs.add(mob);
            unassigned.add(mob);
            densityManager.startTracking(mob);
        }
    }

    public void removeMob(MobEntity mob) {
        synchronized (this) {
            mobs.remove(mob);
            unassigned.remove(mob);
            densityManager.stopTracking(mob);

            UUID target = assigned.remove(mob);

            if (target != null) {
                pursuit.remove(target, mob);

                updatePursuerCount(target);
            }
        }
    }

    public void removeParticipant(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();

        synchronized (this) {
            leastPursuers.remove(uuid);

            var pursuers = pursuit.removeAll(uuid);

            for (MobEntity pursuer : pursuers) {
                assigned.remove(pursuer);
            }
        }
    }

    private void setPursuing(MobEntity mob, LivingEntity target) {
        UUID playerId = target.getUuid();

        synchronized (this) {
            UUID oldTargetId = assigned.put(mob, playerId);

            if (oldTargetId != null && !oldTargetId.equals(playerId)) {
                // the mob pursuit target changed, remove it from the old target pursuers
                pursuit.remove(oldTargetId, mob);
            }

            pursuit.put(playerId, mob);
            unassigned.remove(mob);
            mob.setTarget(target);
            scoreboardManager.joinTeam(mob, targetTeam);

            updatePursuerCount(playerId);
        }
    }

    private void updatePursuerCount(UUID playerUuid) {
        synchronized (this) {
            // if this becomes to heavy, optimize with custom min heap implementation with a changeKey method
            leastPursuers.remove(playerUuid);
            leastPursuers.offer(playerUuid);
        }
    }

    public void update() {
        densityManager.update();

        synchronized (this) {
            // for each player, find the closest mobs and assign n to pursue them
            for (ServerPlayerEntity player : participants) {
                newPursuers.clear();

                findNearestMobs(player)
                        .limit(PURSUERS_PER_PLAYER)
                        .forEachOrdered(newPursuers::add);

                setPursuers(player, newPursuers);
            }
        }
    }

    /**
     * Finds closest mobs to a player. The result should be ordered ascending by distance to the player.
     * @param player The player.
     * @return A list of mobs closest to the player. Ordered ascending by distance.
     */
    private Stream<MobEntity> findNearestMobs(ServerPlayerEntity player) {
        // if this gets to heavy, optimize using spatial data structure like BVH, grid or other
        return mobs.parallelStream()
                .sorted(Comparator.comparingDouble(mob -> mob.squaredDistanceTo(player)))
                .sequential();
    }

    /**
     * Sets the pursuers of a player.
     * This will remove any previous pursuers that are not in the new pursuers.
     * @param player The player.
     * @param newPursuers The pursuers.
     */
    private void setPursuers(ServerPlayerEntity player, Collection<MobEntity> newPursuers) {
        UUID uuid = player.getUuid();

        synchronized (this) {
            pursuit.get(uuid).removeIf(mob -> {
                // check if the mob is already a pursuer of that player
                if (newPursuers.remove(mob)) return false;

                // the mob should no longer be a pursuer of that player
                assigned.remove(mob);
                unassigned.add(mob);
                mob.setTarget(null);
                scoreboardManager.leaveTeam(mob, targetTeam);

                return true;
            });

            // add remaining pursuers
            for (MobEntity mob : newPursuers) {
                setPursuing(mob, player);
            }

            updatePursuerCount(uuid);
        }
    }

    public MobDensityManager getDensityManager() {
        return densityManager;
    }

    public Team getGuardTeam() {
        return guardTeam;
    }

    public CustomScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }
}
