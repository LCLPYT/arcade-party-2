package work.lclpnet.ap2.impl.game;

import net.minecraft.server.world.ServerWorld;
import work.lclpnet.lobby.game.api.MapOptions;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

public class BootstrapMapOptions implements MapOptions {

    private final BiFunction<ServerWorld, GameMap, CompletableFuture<Void>> action;

    public BootstrapMapOptions(BiFunction<ServerWorld, GameMap, CompletableFuture<Void>> action) {
        this.action = action;
    }

    @Override
    public boolean shouldBeDeleted() {
        return true;
    }

    @Override
    public boolean isCleanMapRequired() {
        return true;
    }

    @Override
    public CompletableFuture<Void> bootstrapWorld(ServerWorld world, GameMap map) {
        return action.apply(world, map);
    }
}
