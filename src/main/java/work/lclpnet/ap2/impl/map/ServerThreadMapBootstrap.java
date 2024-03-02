package work.lclpnet.ap2.impl.map;

import net.minecraft.server.world.ServerWorld;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.api.map.MapBootstrapFunction;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.concurrent.CompletableFuture;

/**
 * A map bootstrap that executes on the server thread on the next server tick.
 * This introduces a delay until the next tick is executed.
 * However, this can be useful e.g. when the bootstrap has read a lot of block states, which is typically faster on the server thread.
 */
public class ServerThreadMapBootstrap implements MapBootstrap {

    private final MapBootstrapFunction op;

    public ServerThreadMapBootstrap(MapBootstrapFunction op) {
        this.op = op;
    }

    @Override
    public CompletableFuture<Void> createWorldBootstrap(ServerWorld world, GameMap map) {
        var future = new CompletableFuture<Void>();

        // execute the operation on the next server tick, this introduces a delay
        world.getServer().submit(() -> {
            op.bootstrapWorld(world, map);
            future.complete(null);
        });

        return future;
    }
}
