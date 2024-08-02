package work.lclpnet.ap2.impl.bootstrap;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.data.DataManager;
import work.lclpnet.ap2.api.map.MapFacade;
import work.lclpnet.ap2.api.map.MapFrequencyManager;
import work.lclpnet.ap2.api.map.MapRandomizer;
import work.lclpnet.ap2.api.util.music.SongManager;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.ap2.base.config.Ap2Config;
import work.lclpnet.ap2.base.config.ConfigManager;
import work.lclpnet.ap2.impl.data.JsonDataSource;
import work.lclpnet.ap2.impl.data.MapDynamicData;
import work.lclpnet.ap2.impl.data.MutableDataManager;
import work.lclpnet.ap2.impl.map.BalancedMapRandomizer;
import work.lclpnet.ap2.impl.map.MapFacadeImpl;
import work.lclpnet.ap2.impl.map.SqliteAsyncMapFrequencyManager;
import work.lclpnet.ap2.impl.util.music.SongManagerImpl;
import work.lclpnet.lobby.game.api.GameEnvironment;
import work.lclpnet.lobby.game.api.WorldFacade;
import work.lclpnet.lobby.game.map.*;
import work.lclpnet.lobby.game.map.cache.CacheMapRepository;
import work.lclpnet.lobby.game.map.cache.MapCache;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

public class ApBootstrap {

    private final Path cacheDirectory;
    private final Logger logger;
    private volatile MapCache mapCache = null;

    public ApBootstrap(Path cacheDirectory, Logger logger) {
        this.cacheDirectory = cacheDirectory;
        this.logger = logger;
    }

    public CompletableFuture<ConfigManager> loadConfig(Executor executor) {
        Path configPath = Path.of("config")
                .resolve(ApConstants.ID)
                .resolve("config.json");

        ConfigManager configManager = new ConfigManager(configPath, logger);

        return configManager.init(executor).thenApply(nil -> configManager);
    }

    public MapManager createMapManager(Ap2Config config) {
        var repositories = config.mapsSource.stream()
                .map(this::createMapRepo)
                .toArray(MapRepository[]::new);

        if (repositories.length == 0) {
            throw new IllegalStateException("Map sources not configured");
        }

        MapRepository mapRepository = new MultiMapRepository(repositories);

        var lookup = new RepositoryMapLookup(mapRepository);

        return new MapManager(lookup);
    }

    private MapRepository createMapRepo(URI uri) {
        UriMapRepository repo = new UriMapRepository(uri, logger);

        // if uri is remote, use cache repository
        if (uri.getHost() == null) {
            return repo;
        }

        URL url;

        try {
            url = uri.toURL();
        } catch (MalformedURLException e) {
            return repo;
        }

        if ("file".equalsIgnoreCase(url.getProtocol())) {
            return repo;
        }

        MapCache mapCache = getMapCache();

        if (mapCache == null) {
            return repo;
        }

        return new CacheMapRepository(repo, mapCache);
    }

    @Nullable
    private MapCache getMapCache() {
        if (mapCache != null) {
            return mapCache;
        }

        synchronized (this) {
            if (mapCache != null) {
                return mapCache;
            }

            try {
                mapCache = MapCache.createUserCache(logger);
                return mapCache;
            } catch (IOException e) {
                logger.error("Failed to create map cache", e);
                return null;
            }
        }
    }

    @NotNull
    public MapFacade createMapFacade(MinecraftServer server, MapManager mapManager, WorldFacade worldFacade,
                                     MapRandomizer mapRandomizer) {
        return new MapFacadeImpl(worldFacade, mapRandomizer, mapManager, server, logger);
    }

    @NotNull
    public BalancedMapRandomizer createBalancedMapRandomizer(MapManager mapManager, MapFrequencyManager frequencyTracker) {
        return new BalancedMapRandomizer(mapManager, frequencyTracker, new Random());
    }

    @NotNull
    public SqliteAsyncMapFrequencyManager createSqliteAsyncMapFrequencyManager(String pluginId, GameEnvironment environment) {
        Path dbPath = Path.of("config")
                .resolve(pluginId)
                .resolve("map_frequencies.sqlite");

        var manager = new SqliteAsyncMapFrequencyManager(dbPath, logger);

        environment.whenDone(() -> {
            try {
                manager.close();
            } catch (Exception e) {
                logger.error("Failed to close frequency manager", e);
            }
        });

        return manager;
    }

    public CompletableFuture<Result> dispatch(Ap2Config config, GameEnvironment environment) {
        MinecraftServer server = environment.getServer();
        MapManager mapManager = createMapManager(config);

        WorldFacade worldFacade = environment.getWorldFacade(() -> mapManager);

        var frequencyManager = createSqliteAsyncMapFrequencyManager(ApConstants.ID, environment);
        var randomizer = createBalancedMapRandomizer(mapManager, frequencyManager);
        MapFacade mapFacade = createMapFacade(server, mapManager, worldFacade, randomizer);

        Path songsDir = cacheDirectory.resolve("songs");

        SongManagerImpl songManager = new SongManagerImpl(songsDir);
        MutableDataManager dataManager = new MutableDataManager();

        var mapTask = loadAp2Maps(mapManager);
        var sqliteTask = loadSqlite(frequencyManager, server);
        var songTask = loadSongs(songManager, config);
        var containerTask = loadContainer(dataManager);

        return CompletableFuture.supplyAsync(() -> {
            mapTask.join();
            sqliteTask.join();
            songTask.join();
            containerTask.join();

            return new Result(worldFacade, mapFacade, songManager, dataManager);
        });
    }

    @NotNull
    public CompletableFuture<Void> loadAp2Maps(MapManager mapManager) {
        return loadMaps(mapManager, new MapDescriptor(ApConstants.ID, ""), ForkJoinPool.commonPool());
    }

    @NotNull
    public CompletableFuture<Void> loadMaps(MapManager mapManager, MapDescriptor descriptor, Executor executor) {
        return CompletableFuture.runAsync(() -> {
            try {
                // load general arcade party 2 maps
                mapManager.loadAll(descriptor);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load maps of namespace %s".formatted(ApConstants.ID), e);
            }
        }, executor);
    }

    @NotNull
    public CompletableFuture<Void> loadSqlite(SqliteAsyncMapFrequencyManager frequencyManager, MinecraftServer server) {
        return frequencyManager.open(server)
                .exceptionally(throwable -> {
                    logger.error("Failed to establish sqlite connection. Fallback to StubMapFrequencyManager", throwable);
                    return null;
                });
    }

    @NotNull
    public CompletableFuture<Void> loadSongs(SongManagerImpl songManager, Ap2Config config) {
        return CompletableFuture.runAsync(() -> {
            int i = 0;

            for (var entry : config.songSources.entries()) {
                Identifier tag = entry.getKey();
                URI uri = entry.getValue();

                try {
                    songManager.loadBundleSync(tag, uri, i++, logger);
                } catch (IOException e) {
                    logger.error("Failed to load song bundle with tag {} from {}", tag, uri, e);
                }
            }
        }).exceptionally(err -> {
            logger.error("Failed to load songs", err);
            return null;
        });
    }

    @NotNull
    public CompletableFuture<Void> loadContainer(MutableDataManager dataManager) {
        return CompletableFuture.runAsync(() -> dataManager.setData(MapDynamicData.builder()
                .addSource(new JsonDataSource(this::openConfigurationFile, logger))
                .build()));
    }

    public InputStream openConfigurationFile() {
        return Objects.requireNonNull(getClass().getResourceAsStream("/configuration.json"), "File not found: configuration.json");
    }

    public record Result(WorldFacade worldFacade, MapFacade mapFacade, SongManager songManager,
                         DataManager dataManager) {}
}
