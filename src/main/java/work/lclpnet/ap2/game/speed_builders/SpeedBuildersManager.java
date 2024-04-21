package work.lclpnet.ap2.game.speed_builders;

import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.speed_builders.data.SbIsland;

import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

public class SpeedBuildersManager {

    private final Map<UUID, SbIsland> islands;
    private final MiniGameHandle gameHandle;

    public SpeedBuildersManager(Map<UUID, SbIsland> islands, MiniGameHandle gameHandle) {
        this.islands = islands;
        this.gameHandle = gameHandle;
    }

    public void eachIsland(BiConsumer<SbIsland, ServerPlayerEntity> action) {
        PlayerManager playerManager = gameHandle.getServer().getPlayerManager();

        islands.forEach((uuid, island) -> {
            ServerPlayerEntity player = playerManager.getPlayer(uuid);

            if (player != null) {
                action.accept(island, player);
            }
        });
    }
}
