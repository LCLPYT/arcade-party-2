package work.lclpnet.ap2.api.map;

import net.minecraft.server.world.ServerWorld;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.concurrent.CompletableFuture;

public interface MapBootstrap {

    MapBootstrap NONE = (world, map) -> CompletableFuture.completedFuture(null);

    CompletableFuture<Void> createWorldBootstrap(ServerWorld world, GameMap map);
}
