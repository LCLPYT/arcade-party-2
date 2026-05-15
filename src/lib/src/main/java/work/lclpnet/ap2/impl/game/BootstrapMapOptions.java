package work.lclpnet.ap2.impl.game;

import net.minecraft.server.level.ServerLevel;
import work.lclpnet.game.api.MapOptions;
import work.lclpnet.game.map.GameMap;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

public class BootstrapMapOptions implements MapOptions {

    private final BiFunction<ServerLevel, GameMap, CompletableFuture<Void>> action;

    public BootstrapMapOptions(BiFunction<ServerLevel, GameMap, CompletableFuture<Void>> action) {
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
    public CompletableFuture<Void> bootstrapWorld(ServerLevel world, GameMap map) {
        return action.apply(world, map);
    }
}
