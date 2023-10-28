package work.lclpnet.ap2.impl.map;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameRules;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.game.MapReady;
import work.lclpnet.ap2.api.map.MapFacade;
import work.lclpnet.ap2.api.map.MapRandomizer;
import work.lclpnet.lobby.game.api.WorldFacade;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapDescriptor;

import java.util.concurrent.CompletableFuture;

public class MapFacadeImpl implements MapFacade {

    private final WorldFacade worldFacade;
    private final MapRandomizer mapRandomizer;
    private final MinecraftServer server;
    private final Logger logger;

    public MapFacadeImpl(WorldFacade worldFacade, MapRandomizer mapRandomizer, MinecraftServer server, Logger logger) {
        this.worldFacade = worldFacade;
        this.mapRandomizer = mapRandomizer;
        this.server = server;
        this.logger = logger;
    }

    @Override
    public CompletableFuture<ServerWorld> openRandomMap(Identifier gameId) {
        return mapRandomizer.nextMap(gameId)
                .thenApply(GameMap::getDescriptor)
                .thenApply(MapDescriptor::getIdentifier)
                .thenCompose(worldFacade::changeMap)
                .thenApply(this::setupWorld);
    }

    @Override
    public void openRandomMap(Identifier gameId, MapReady callback) {
        openRandomMap(gameId)
                .thenCompose(world -> server.submit(() -> callback.onReady(world)))
                .exceptionally(throwable -> {
                    logger.error("Failed to open a random map for game {}", gameId, throwable);
                    return null;
                });
    }

    private ServerWorld setupWorld(ServerWorld world) {
        world.getGameRules().get(GameRules.DO_IMMEDIATE_RESPAWN).set(true, server);

        return world;
    }
}
