package work.lclpnet.ap2.impl.map;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameRules;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.game.MapReady;
import work.lclpnet.ap2.api.map.MapFacade;
import work.lclpnet.ap2.api.map.MapRandomizer;
import work.lclpnet.lobby.game.api.MapOptions;
import work.lclpnet.lobby.game.api.WorldFacade;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapDescriptor;
import work.lclpnet.lobby.game.map.MapManager;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MapFacadeImpl implements MapFacade {

    private final WorldFacade worldFacade;
    private final MapRandomizer mapRandomizer;
    private final MapManager mapManager;
    private final MinecraftServer server;
    private final Logger logger;

    public MapFacadeImpl(WorldFacade worldFacade, MapRandomizer mapRandomizer, MapManager mapManager,
                         MinecraftServer server, Logger logger) {
        this.worldFacade = worldFacade;
        this.mapRandomizer = mapRandomizer;
        this.mapManager = mapManager;
        this.server = server;
        this.logger = logger;
    }

    @Override
    public CompletableFuture<Pair<ServerWorld, GameMap>> openRandomMap(Identifier gameId, MapOptions mapOptions) {
        return mapRandomizer.nextMap(gameId)
                .thenCompose(map -> {
                    Identifier id = map.getDescriptor().getIdentifier();
                    return worldFacade.changeMap(id, mapOptions).thenApply(world -> Pair.of(world, map));
                })
                .thenApply(pair -> {
                    setupWorld(pair.left());
                    return pair;
                });
    }

    @Override
    public void openRandomMap(Identifier gameId, MapOptions options, MapReady callback) {
        openRandomMap(gameId, options)
                .thenCompose(pair -> server.submit(() -> callback.onReady(pair.left(), pair.right())))
                .exceptionally(throwable -> {
                    logger.error("Failed to open a random map for game {}", gameId, throwable);
                    return null;
                });
    }

    @Override
    public CompletableFuture<List<Identifier>> getMapIds(Identifier gameId) {
        List<Identifier> mapIds = mapManager.getCollection()
                .mapIdsWithPrefix(gameId)
                .sorted()
                .toList();

        return CompletableFuture.completedFuture(mapIds);
    }

    @Override
    public CompletableFuture<List<GameMap>> getMaps(Identifier gameId) {
        List<GameMap> maps = mapManager.getCollection()
                .mapsWithPrefix(gameId)
                .sorted(Comparator.comparing(map -> map.getDescriptor().getIdentifier()))
                .toList();

        return CompletableFuture.completedFuture(maps);
    }

    @Override
    public CompletableFuture<Void> reloadMaps(Identifier gameId) {
        return CompletableFuture.runAsync(() -> {
            try {
                mapManager.loadAll(new MapDescriptor(gameId));
            } catch (IOException e) {
                throw new RuntimeException("Failed to reload maps for game id %s".formatted(gameId), e);
            }
        });
    }

    @Override
    public void forceMap(Identifier mapId) {
        mapRandomizer.forceMap(mapId);
    }

    private void setupWorld(ServerWorld world) {
        GameRules gameRules = world.getGameRules();
        gameRules.get(GameRules.DO_IMMEDIATE_RESPAWN).set(true, server);
        gameRules.get(GameRules.ANNOUNCE_ADVANCEMENTS).set(false, server);
    }
}
