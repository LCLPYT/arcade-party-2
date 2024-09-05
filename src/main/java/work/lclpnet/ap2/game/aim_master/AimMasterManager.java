package work.lclpnet.ap2.game.aim_master;

import net.minecraft.server.network.ServerPlayerEntity;
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

    public void advancePlayer(ServerPlayerEntity player) {
        var playerUuid = player.getUuid();

        AimMasterDomain domain = domains.get(playerUuid);

        int progress = getPlayerProgress(player);
        int newProgress = progress + 1;

        domain.removeBlocks(sequence.getItems().get(progress));
        domain.setBlocks(sequence.getItems().get(newProgress), player);
        playerProgress.put(playerUuid, newProgress);
    }

    public int getPlayerProgress(ServerPlayerEntity player) {
        return playerProgress.getOrDefault(player.getUuid(), 0);}
}
