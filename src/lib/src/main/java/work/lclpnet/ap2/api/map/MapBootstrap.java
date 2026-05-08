package work.lclpnet.ap2.api.map;

import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.concurrent.CompletableFuture;

public interface MapBootstrap {

    MapBootstrap NONE = (_, _) -> CompletableFuture.completedFuture(null);

    @NotNull
    CompletableFuture<Void> createWorldBootstrap(@NotNull ServerLevel world, @NotNull GameMap map);
}
