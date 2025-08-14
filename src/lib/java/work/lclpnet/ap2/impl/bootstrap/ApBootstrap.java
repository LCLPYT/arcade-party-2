package work.lclpnet.ap2.impl.bootstrap;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.config.Ap2Config;
import work.lclpnet.ap2.api.config.ConfigManager;
import work.lclpnet.ap2.api.data.DataManager;
import work.lclpnet.ap2.api.map.MapFacade;
import work.lclpnet.ap2.api.map.MapFrequencyManager;
import work.lclpnet.ap2.api.map.MapRandomizer;
import work.lclpnet.ap2.api.util.music.SongManager;
import work.lclpnet.ap2.impl.data.JsonDataSource;
import work.lclpnet.ap2.impl.data.MapDynamicData;
import work.lclpnet.ap2.impl.data.MutableDataManager;
import work.lclpnet.ap2.impl.i18n.VanillaTranslations;
import work.lclpnet.ap2.impl.map.BalancedMapRandomizer;
import work.lclpnet.ap2.impl.map.MapFacadeImpl;
import work.lclpnet.ap2.impl.map.SqliteAsyncMapFrequencyManager;
import work.lclpnet.ap2.impl.util.music.SongManagerImpl;
import work.lclpnet.config.json.JsonConfigFactory;
import work.lclpnet.lobby.game.api.GameEnvironment;
import work.lclpnet.lobby.game.api.WorldFacade;
import work.lclpnet.lobby.game.asset.*;
import work.lclpnet.lobby.game.asset.cache.AssetCache;
import work.lclpnet.lobby.game.map.AssetMapRepository;
import work.lclpnet.lobby.game.map.MapDescriptor;
import work.lclpnet.lobby.game.map.MapManager;
import work.lclpnet.lobby.game.map.RepositoryMapLookup;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

import static java.util.concurrent.CompletableFuture.runAsync;

public class ApBootstrap {

    private static final int CACHE_TTL_SECONDS = 3600;

    private final Path cacheDirectory;
    private final JsonConfigFactory<Ap2Config> configFactory;
    private final Logger logger;

    public ApBootstrap(Path cacheDirectory, JsonConfigFactory<Ap2Config> configFactory, Logger logger) {
        this.cacheDirectory = cacheDirectory;
        this.configFactory = configFactory;
        this.logger = logger;
    }

    public CompletableFuture<ConfigManager> loadConfig(Executor executor) {
        Path configPath = Path.of("config")
                .resolve(ApConstants.ID)
                .resolve("config.json");

        ConfigManager configManager = new ConfigManager(configPath, configFactory, logger);

        return configManager.init(executor).thenApply(nil -> configManager);
    }

    public MapManagerHandle createMapManager(Ap2Config config) {
        AssetCache cache = createAssetCache(CommonAssets.MAPS);

        var repositories = config.mapsSource.stream()
                .map(uri -> createAssetRepo(uri, cache))
                .toArray(AssetRepository[]::new);

        if (repositories.length == 0) {
            throw new IllegalStateException("Map sources not configured");
        }

        var assetRepo = new MultiAssetRepository(repositories, logger);
        var mapRepo = new AssetMapRepository(assetRepo, logger);

        var lookup = new RepositoryMapLookup(mapRepo);

        return new MapManagerHandle(new MapManager(lookup, logger), cache);
    }

    private AssetRepository createAssetRepo(URI uri, @Nullable AssetCache cache) {
        var repo = new UriAssetRepository(uri, logger);

        // if uri is remote, use cache repository
        if (cache == null || uri.getHost() == null) {
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

        return new CacheAssetRepository(cache, repo, CACHE_TTL_SECONDS, logger);
    }

    @Nullable
    private AssetCache createAssetCache(String type) {
        try {
            return AssetCache.createUserCache(type, logger);
        } catch (IOException e) {
            logger.error("Failed to create map cache", e);
            return null;
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

    public CompletableFuture<Result> dispatch(Ap2Config config, GameEnvironment environment,
                                              VanillaTranslations vanillaTranslations) {

        MinecraftServer server = environment.getServer();

        @SuppressWarnings("resource")
        MapManagerHandle handle = createMapManager(config);

        environment.whenDone(() -> {
            try {
                handle.close();
            } catch (Exception e) {
                logger.error("Failed to close MapManagerHandle", e);
            }
        });

        MapManager mapManager = handle.mapManager();

        WorldFacade worldFacade = environment.getWorldFacade(() -> mapManager);

        var frequencyManager = createSqliteAsyncMapFrequencyManager(ApConstants.ID, environment);
        var randomizer = createBalancedMapRandomizer(mapManager, frequencyManager);
        MapFacade mapFacade = createMapFacade(server, mapManager, worldFacade, randomizer);

        Path songsDir = cacheDirectory.resolve("songs");

        SongManagerImpl songManager = new SongManagerImpl(songsDir, logger);
        MutableDataManager dataManager = new MutableDataManager();

        var mapTask = loadAp2Maps(mapManager);
        var sqliteTask = loadSqlite(frequencyManager, server);
        var songTask = loadSongs(songManager, config);
        var containerTask = loadContainer(dataManager);
        var vanillaTranslationsTask = runAsync(vanillaTranslations::init);

        return CompletableFuture.supplyAsync(() -> {
            mapTask.join();
            sqliteTask.join();
            songTask.join();
            containerTask.join();
            vanillaTranslationsTask.join();

            return new Result(worldFacade, mapFacade, songManager, dataManager);
        });
    }

    @NotNull
    public CompletableFuture<Void> loadAp2Maps(MapManager mapManager) {
        return loadMaps(mapManager, new MapDescriptor(ApConstants.ID, ""), ForkJoinPool.commonPool());
    }

    @NotNull
    public CompletableFuture<Void> loadMaps(MapManager mapManager, MapDescriptor descriptor, Executor executor) {
        return runAsync(() -> {
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
        return runAsync(() -> {
            for (var entry : config.songSources.entrySet()) {
                Identifier tag = entry.getKey();
                List<URI> uris = entry.getValue();

                for (int i = 0; i < uris.size(); i++) {
                    URI uri = uris.get(i);

                    try {
                        songManager.loadBundleSync(tag, uri, i);
                    } catch (IOException e) {
                        logger.error("Failed to load song bundle with tag {} from {}", tag, uri, e);
                    }
                }
            }
        }).exceptionally(err -> {
            logger.error("Failed to load songs", err);
            return null;
        });
    }

    @NotNull
    public CompletableFuture<Void> loadContainer(MutableDataManager dataManager) {
        return runAsync(() -> dataManager.setData(MapDynamicData.builder()
                .addSource(new JsonDataSource(this::openConfigurationFile, logger))
                .build()));
    }

    public InputStream openConfigurationFile() {
        return Objects.requireNonNull(getClass().getResourceAsStream("/configuration.json"), "File not found: configuration.json");
    }

    public record MapManagerHandle(MapManager mapManager, AssetCache cache) implements AutoCloseable {
        @Override
        public void close() throws Exception {
            cache.close();
        }
    }

    public record Result(WorldFacade worldFacade, MapFacade mapFacade, SongManager songManager,
                         DataManager dataManager) {}
}
