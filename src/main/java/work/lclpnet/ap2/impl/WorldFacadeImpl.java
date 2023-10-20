package work.lclpnet.ap2.impl;

import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.level.storage.LevelStorage;
import org.apache.commons.io.FileUtils;
import work.lclpnet.ap2.Prototype;
import work.lclpnet.ap2.api.WorldFacade;
import work.lclpnet.kibu.hook.ServerPlayConnectionHooks;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.world.KibuWorlds;
import work.lclpnet.kibu.world.mixin.MinecraftServerAccessor;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapManager;
import work.lclpnet.lobby.util.WorldContainer;
import xyz.nucleoid.fantasy.RuntimeWorldHandle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Prototype
public class WorldFacadeImpl implements WorldFacade {

    private final MinecraftServer server;
    private final MapManager mapManager;
    private final WorldContainer worldContainer;
    private final WorldUnloader worldUnloader;
    private RegistryKey<World> mapKey = null;
    private Vec3d spawn = null;

    public WorldFacadeImpl(MinecraftServer server, MapManager mapManager, WorldContainer worldContainer) {
        this.server = server;
        this.mapManager = mapManager;
        this.worldContainer = worldContainer;
        this.worldUnloader = new WorldUnloader(server, worldContainer);
    }

    public void init(HookRegistrar registrar) {
        registrar.registerHook(ServerPlayConnectionHooks.JOIN, this::onPlayerJoin);

        worldUnloader.init(registrar);
    }

    private void onPlayerJoin(ServerPlayNetworkHandler serverPlayNetworkHandler, PacketSender packetSender, MinecraftServer server) {
        if (mapKey == null || spawn == null) return;

        ServerPlayerEntity player = serverPlayNetworkHandler.getPlayer();
        ServerWorld world = server.getWorld(mapKey);

        if (world == null) {
            throw new IllegalStateException("World %s is not loaded".formatted(mapKey.getValue()));
        }

        player.teleport(world, spawn.getX(), spawn.getY(), spawn.getZ(), 0F, 0F);
    }

    @Override
    public CompletableFuture<Void> changeMap(Identifier identifier) {
        var map = mapManager.getMapCollection().getMap(identifier);

        if (map.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Unknown map %s".formatted(identifier)));
        }

        var newKey = RegistryKey.of(RegistryKeys.WORLD, identifier);

        if (server.getWorld(newKey) != null) {
            return worldUnloader.unloadMap(newKey)
                    .thenCompose(nil -> changeToYetUnloadedMap(map.get(), newKey));
        }

        return changeToYetUnloadedMap(map.get(), newKey);
    }

    private CompletableFuture<Void> changeToYetUnloadedMap(GameMap map, RegistryKey<World> newKey) {
        LevelStorage.Session session = ((MinecraftServerAccessor) server).getSession();
        Path directory = session.getWorldDirectory(newKey);

        return CompletableFuture.runAsync(() -> {
            try {
                if (Files.exists(directory)) {
                    FileUtils.forceDelete(directory.toFile());
                }

                mapManager.pull(map, directory);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }).thenCompose(nil -> server.submit(() -> {
            var optHandle = KibuWorlds.getInstance().getWorldManager(server).openPersistentWorld(newKey.getValue());

            RuntimeWorldHandle handle = optHandle.orElseThrow(() -> new IllegalStateException("Failed to load map"));

            worldContainer.trackHandle(handle);  // automatically unload world, if not done manually

            ServerWorld world = handle.asWorld();

            this.mapKey = newKey;
            this.spawn = MapUtils.getSpawnPosition(map);

            for (ServerPlayerEntity player : PlayerLookup.all(server)) {
                player.teleport(world, spawn.getX(), spawn.getY(), spawn.getZ(), Set.of(), 0, 0);
            }
        }));
    }

    @Override
    public void deleteMap() {
        // TODO implement
    }
}
