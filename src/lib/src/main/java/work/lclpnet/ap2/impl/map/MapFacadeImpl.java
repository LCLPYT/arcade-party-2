package work.lclpnet.ap2.impl.map;

import it.unimi.dsi.fastutil.Pair;
import lombok.Getter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.game.MapReady;
import work.lclpnet.ap2.api.map.MapFacade;
import work.lclpnet.ap2.api.map.MapRandomizer;
import work.lclpnet.ap2.game.util.GameLevelsKt;
import work.lclpnet.gaco.asset.AssetRepository;
import work.lclpnet.game.api.WorldFacade;
import work.lclpnet.game.api.WorldOptions;
import work.lclpnet.game.map.GameMap;
import work.lclpnet.game.map.MapDescriptor;
import work.lclpnet.game.map.MapManager;
import work.lclpnet.game.map.MapUtils;
import work.lclpnet.kibu.hook.util.PositionRotation;
import work.lclpnet.kibu.world.KibuLevels;
import work.lclpnet.kibu.world.mixin.MinecraftServerAccessor;
import xyz.nucleoid.fantasy.RuntimeLevelHandle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class MapFacadeImpl implements MapFacade {

    private final WorldFacade worldFacade;
    private final MapRandomizer mapRandomizer;
    private final MapManager mapManager;
    @Getter
    private final AssetRepository assetRepository;
    private final MinecraftServer server;
    private final Logger logger;

    public MapFacadeImpl(WorldFacade worldFacade, MapRandomizer mapRandomizer, MapManager mapManager,
                         AssetRepository assetRepository, MinecraftServer server, Logger logger) {
        this.worldFacade = worldFacade;
        this.mapRandomizer = mapRandomizer;
        this.mapManager = mapManager;
        this.assetRepository = assetRepository;
        this.server = server;
        this.logger = logger;
    }

    @Override
    public CompletableFuture<ServerLevel> changeMap(Identifier identifier, WorldOptions options) {
        var optMap = mapManager.collection().getMap(identifier);

        if (optMap.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Unknown map %s".formatted(identifier)));
        }

        GameMap map = optMap.get();

        return worldFacade.changeLevel(
                identifier,
                options,
                _ -> {
                    Vec3 pos = MapUtils.getSpawnPosition(map);
                    float yaw = MapUtils.getSpawnYaw(map);
                    PositionRotation spawn = new PositionRotation(pos.x(), pos.y(), pos.z(), yaw, 0f);

                    return CompletableFuture.completedFuture(spawn);
                },
                key -> changeToYetUnloadedMap(map, key)
        );
    }

    private CompletableFuture<RuntimeLevelHandle> changeToYetUnloadedMap(GameMap map, ResourceKey<Level> key) {
        LevelStorageSource.LevelStorageAccess session = ((MinecraftServerAccessor) server).getStorageSource();
        Path directory = session.getDimensionPath(key);

        return CompletableFuture.runAsync(() -> prepareMapFiles(map, directory))
                .thenComposeAsync(_ -> loadMap(key));
    }

    private void prepareMapFiles(GameMap map, Path directory) {
        try {
            if (Files.exists(directory)) {
                FileUtils.forceDelete(directory.toFile());
            }

            mapManager.pull(map, directory);
        } catch (IOException e) {
            throw new CompletionException(e);
        }
    }

    private @NonNull CompletableFuture<RuntimeLevelHandle> loadMap(ResourceKey<Level> key) {
        return server.submit(() -> KibuLevels.getInstance()
                .getWorldManager(server)
                .openPersistentLevel(key.identifier())
                .orElseThrow(() -> new IllegalStateException("Failed to load map"))
        );
    }

    @Override
    public CompletableFuture<Pair<ServerLevel, GameMap>> openRandomMap(Identifier gameId, WorldOptions options) {
        return mapRandomizer.nextMap(gameId)
                .thenCompose(map -> {
                    Identifier id = map.getDescriptor().getIdentifier();
                    return changeMap(id, options).thenApply(world -> Pair.of(world, map));
                })
                .thenApply(pair -> {
                    GameLevelsKt.setupGameLevel(pair.left());
                    return pair;
                });
    }

    @Override
    public void openRandomMap(Identifier gameId, WorldOptions options, MapReady callback) {
        openRandomMap(gameId, options)
                .thenCompose(pair -> server.submit(() -> callback.onReady(pair.left(), pair.right())))
                .exceptionally(throwable -> {
                    logger.error("Failed to open a random map for game {}", gameId, throwable);
                    return null;
                });
    }

    @Override
    public CompletableFuture<List<Identifier>> getMapIds(Identifier gameId) {
        List<Identifier> mapIds = mapManager.collection()
                .mapIdsWithPrefix(gameId)
                .sorted()
                .toList();

        return CompletableFuture.completedFuture(mapIds);
    }

    @Override
    public CompletableFuture<List<GameMap>> getMaps(Identifier gameId) {
        List<GameMap> maps = mapManager.collection()
                .mapsWithPrefix(gameId)
                .sorted(Comparator.comparing(map -> map.getDescriptor().getIdentifier()))
                .toList();

        return CompletableFuture.completedFuture(maps);
    }

    @Override
    public CompletableFuture<Optional<GameMap>> getMap(Identifier mapId) {
        var optMap = mapManager.collection().getMap(mapId);

        return CompletableFuture.completedFuture(optMap);
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
    public void forceMap(@Nullable Identifier mapId) {
        mapRandomizer.forceMap(mapId);
    }
}
