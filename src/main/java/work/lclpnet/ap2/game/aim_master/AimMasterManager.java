package work.lclpnet.ap2.game.aim_master;

import net.minecraft.server.world.ServerWorld;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AimMasterManager {

    private final Map<UUID, Integer> playerProgress = new HashMap<>();
    private final Map<UUID, AimMasterDomain> domains;
    private final AimMasterSequence sequence;
    private final ServerWorld world;

    public AimMasterManager(ServerWorld world, Map<UUID, AimMasterDomain> domains, AimMasterSequence sequence) {
        this.domains = domains;
        this.sequence = sequence;
        this.world = world;
    }

    public Map<UUID, AimMasterDomain> getDomains() {
        return domains;
    }

    public void advancePlayer(UUID player) {
        AimMasterDomain domain = domains.get(player);

        int progress = getPlayerProgress(player);
        int newProgress = progress + 1;

        domain.removeBlocks(sequence.getItems().get(progress));
        domain.setBlocks(sequence.getItems().get(newProgress));
        playerProgress.put(player, newProgress);
    }

    public int getPlayerProgress(UUID player) {
        return playerProgress.getOrDefault(player, 0);}
}
