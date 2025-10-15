package work.lclpnet.ap2.impl.bootstrap;

import net.minecraft.util.Identifier;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.config.Ap2Config;
import work.lclpnet.config.json.JsonConfigFactory;
import work.lclpnet.gaco.asset.AssetRepository;
import work.lclpnet.gaco.asset.CommonAssets;
import work.lclpnet.gaco.asset.cache.AssetCache;
import work.lclpnet.lobby.game.api.data.DataPackSink;
import work.lclpnet.lobby.game.api.data.GameDataPacks;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapDescriptor;
import work.lclpnet.lobby.game.map.MapManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

public class ApDataPacks implements GameDataPacks {

    private final Path cacheDirectory;
    private final JsonConfigFactory<Ap2Config> configFactory;
    private final Logger logger;

    public ApDataPacks(Path cacheDirectory, JsonConfigFactory<Ap2Config> configFactory, Logger logger) {
        this.cacheDirectory = cacheDirectory;
        this.configFactory = configFactory;
        this.logger = logger;
    }

    @Override
    public CompletableFuture<Void> downloadPacks(DataPackSink dataPackSink, Executor executor) {
        ApBootstrap bootstrap = new ApBootstrap(configFactory, logger);
        Identifier dataPacksPath = Objects.requireNonNull(Identifier.of("datapacks", ""));

        List<AutoCloseable> resources = new ArrayList<>();

        return bootstrap.loadConfig(executor)
                .thenApplyAsync(configManager -> {
                    Ap2Config config = configManager.getConfig();

                    AssetCache cache = bootstrap.createAssetCache(CommonAssets.MAPS);
                    AssetRepository repo = bootstrap.createMapAssetRepo(config, cache);
                    var mapManager = bootstrap.createMapManager(repo);

                    resources.add(cache);

                    bootstrap.loadMaps(mapManager, new MapDescriptor(dataPacksPath), executor).join();

                    return mapManager;
                }, executor)
                .thenAcceptAsync(mapManager -> {
                    var maps = mapManager.getCollection().mapsWithPrefix(dataPacksPath);

                    fetchDataPacks(mapManager, maps, dataPackSink);
                })
                .whenComplete((nil, err) -> {
                    if (err != null) {
                        logger.error("Failed to locate data packs");
                    }

                    for (AutoCloseable resource : resources) {
                        try {
                            resource.close();
                        } catch (Exception e) {
                            logger.error("Failed to close resource {}", resource, e);
                        }
                    }
                });
    }

    private void fetchDataPacks(MapManager mapManager, Stream<GameMap> maps, DataPackSink sink) {
        var it = maps.iterator();

        Path dir = cacheDirectory.resolve("data_pack_maps");

        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                logger.error("Failed to create directory: {}", dir, e);
                return;
            }
        }

        while (it.hasNext()) {
            GameMap map = it.next();

            Path directory = dir.resolve(map.getDescriptor().getMapPath());

            try {
                if (Files.exists(directory)) {
                    FileUtils.forceDelete(directory.toFile());
                }

                Files.createDirectories(dir.getParent());

                mapManager.pull(map, directory);

                offerPacksFrom(directory, sink);
            } catch (IOException e) {
                logger.error("Failed fetch data packs of map {}: failed to pull", map, e);
            }
        }
    }

    private void offerPacksFrom(Path directory, DataPackSink sink) throws IOException {
        Path packsDir = directory.resolve("datapacks");

        if (!Files.isDirectory(packsDir)) return;

        List<Path> packs;

        try (var files = Files.list(packsDir)) {
            packs = files.filter(file -> file.getFileName().toString().endsWith(".zip"))
                    .filter(Files::isRegularFile)
                    .toList();
        }

        for (Path pack : packs) {
            try (var in = Files.newInputStream(pack)) {
                sink.offer(pack.getFileName(), in);
            } catch (IOException e) {
                logger.error("Failed to copy data pack {}", pack, e);
            }
        }
    }
}
