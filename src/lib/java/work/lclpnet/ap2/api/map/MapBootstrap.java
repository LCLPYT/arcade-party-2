package work.lclpnet.ap2.api.map;

import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.concurrent.CompletableFuture;

public interface MapBootstrap {

    MapBootstrap NONE = (world, map) -> CompletableFuture.completedFuture(null);

    @NotNull
    CompletableFuture<Void> createWorldBootstrap(@NotNull ServerWorld world, @NotNull GameMap map);
}
