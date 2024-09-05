package work.lclpnet.ap2.game.aim_master;

import net.minecraft.server.world.ServerWorld;

import java.util.*;

public class AimMasterManager {

    private static Map<UUID, Integer> playerProgress;

    public AimMasterManager(Map<UUID, AimMasterDomain> domains, ServerWorld world) {

        playerProgress = new HashMap<>();
        List<UUID> players = new ArrayList<>(domains.size());

        players.addAll(domains.keySet());

        for (UUID player : players) {playerProgress.put(player, 0);}
    }

    public void advancePlayer(UUID player) {
        playerProgress.put(player, playerProgress.get(player) + 1);
    }

    public int getPlayerProgress(UUID player) {
        return playerProgress.get(player);}
}
