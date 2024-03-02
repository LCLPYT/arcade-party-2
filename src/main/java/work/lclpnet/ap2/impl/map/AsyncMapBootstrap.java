package work.lclpnet.ap2.impl.map;

import net.minecraft.server.world.ServerWorld;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.api.map.MapBootstrapFunction;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.concurrent.CompletableFuture;

public class AsyncMapBootstrap implements MapBootstrap {

    private final MapBootstrapFunction op;

    public AsyncMapBootstrap(MapBootstrapFunction op) {
        this.op = op;
    }

    @Override
    public CompletableFuture<Void> createWorldBootstrap(ServerWorld world, GameMap map) {
        return CompletableFuture.runAsync(() -> op.bootstrapWorld(world, map));
    }
}
