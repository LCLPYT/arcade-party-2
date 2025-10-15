package work.lclpnet.ap2.impl.util.world;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.level.storage.LevelStorage;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import work.lclpnet.gaco.asset.AssetPath;
import work.lclpnet.gaco.asset.AssetRepository;
import work.lclpnet.gaco.asset.AssetRequestOptions;
import work.lclpnet.gaco.asset.AssetUriResource;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.world.KibuWorlds;
import work.lclpnet.kibu.world.mixin.MinecraftServerAccessor;
import work.lclpnet.lobby.game.impl.WorldContainer;
import work.lclpnet.lobby.game.impl.WorldUnloader;
import work.lclpnet.lobby.io.copy.WorldCopier;
import work.lclpnet.map_api.GameMapApi;
import work.lclpnet.map_api.data.WorldData;
import xyz.nucleoid.fantasy.RuntimeWorldHandle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;

public class SubWorldManager {

    private final AssetRepository repo;
    private final MinecraftServer server;
    private final WorldContainer container;
    private final WorldUnloader unloader;
    private final Logger logger;

    public SubWorldManager(AssetRepository repo, MinecraftServer server, WorldContainer container, Logger logger) {
        this.repo = repo;
        this.server = server;
        this.container = container;
        this.logger = logger;
        this.unloader = new WorldUnloader(server, container);
    }

    public void init(HookRegistrar hooks) {
        unloader.init(hooks);
    }

    public CompletableFuture<ServerWorld> loadWorld(AssetPath path, Identifier id) {
        return CompletableFuture.runAsync(() -> obtainWorld(path, id)).thenCompose(nil -> server.submit(() -> {
            RuntimeWorldHandle handle = KibuWorlds.getInstance().getWorldManager(server)
                    .openPersistentWorld(id)
                    .orElseThrow(() -> new NoSuchElementException("Failed to load world"));

            container.trackHandle(handle);

            return handle.asWorld();
        }));
    }

    public CompletableFuture<WorldWithData> loadWorldWithData(AssetPath path, Identifier id) {
        var key = RegistryKey.of(RegistryKeys.WORLD, id);

        var dataFuture = GameMapApi.get(server).getDataManager().awaitWorldData(key);

        return loadWorld(path, id).thenCompose(world -> dataFuture.thenApply(data -> new WorldWithData(world, data)));
    }

    private void obtainWorld(AssetPath path, Identifier id) {
        var registryKey = RegistryKey.of(RegistryKeys.WORLD, id);

        LevelStorage.Session session = ((MinecraftServerAccessor) server).getSession();
        Path directory = session.getWorldDirectory(registryKey);

        try {
            if (Files.exists(directory)) {
                FileUtils.forceDelete(directory.toFile());
            } else {
                Files.createDirectories(directory.getParent());
            }

            pullWorld(path, directory);
        } catch (IOException e) {
            throw new RuntimeException("Failed to download and extract world files", e);
        }
    }

    private void pullWorld(AssetPath path, Path target) throws IOException {
        var uris = repo.getUris(path, AssetRequestOptions.DEFAULT);

        for (AssetUriResource res : uris) {
            try {
                WorldCopier.get(res.resource()).copyTo(target);
                return;
            } catch (IOException e) {
                logger.debug("Failed to copy world source of world {} to {}", path, target, e);
            }
        }

        throw new IOException("Failed to copy world source: No map could be copied successfully. Please check the debug log for more details");
    }

    public CompletableFuture<Void> unloadWorld(ServerWorld world) {
        return unloader.unloadMap(world.getRegistryKey());
    }

    public record WorldWithData(ServerWorld world, WorldData data) {}
}
