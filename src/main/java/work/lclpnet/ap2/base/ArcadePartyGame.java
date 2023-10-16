package work.lclpnet.ap2.base;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import work.lclpnet.activity.manager.ActivityManager;
import work.lclpnet.ap2.base.activity.PreparationActivity;
import work.lclpnet.lobby.game.Game;
import work.lclpnet.lobby.game.GameEnvironment;
import work.lclpnet.lobby.game.conf.GameConfig;
import work.lclpnet.lobby.game.conf.MinecraftGameConfig;
import work.lclpnet.lobby.game.map.MapCollection;
import work.lclpnet.lobby.game.map.MapManager;
import work.lclpnet.lobby.game.map.MapRepository;
import work.lclpnet.lobby.game.map.UrlMapRepository;
import work.lclpnet.lobby.util.WorldContainer;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class ArcadePartyGame implements Game {

    private final Logger logger = ArcadeParty.logger;
    private final MapManager mapManager;

    public ArcadePartyGame() {
        try {
            this.mapManager = getMapManager();
        } catch (MalformedURLException e) {
            throw new RuntimeException("Failed to setup map repository url", e);
        }
    }

    @Override
    public GameConfig getConfig() {
        return new MinecraftGameConfig("ap2", "Arcade Party", new ItemStack(Items.GOLD_BLOCK));
    }

    @Override
    public boolean canStart(GameEnvironment environment) {
        if (ApConstants.DEVELOPMENT) {
            return true;
        }

        MinecraftServer server = environment.getServer();

        return PlayerLookup.all(server).size() >= 2;
    }

    @Override
    public void start(GameEnvironment environment) {
        MinecraftServer server = environment.getServer();
        ArcadeParty instance = ArcadeParty.getInstance();

        WorldContainer worldContainer = new WorldContainer(server);
        instance.registerUnloadable(worldContainer);

        PreparationActivity preparation = new PreparationActivity(mapManager, worldContainer);

        load().thenRun(() ->
                        server.submit(() ->
                                ActivityManager.getInstance().startActivity(preparation)))
                .exceptionally(throwable -> {
                    logger.error("Failed to load ArcadeParty2", throwable);
                    return null;
                });
    }

    private MapManager getMapManager() throws MalformedURLException {
        MapRepository mapRepository = new UrlMapRepository(Path.of("worlds").toUri().toURL(), logger);

        return new MapManager(mapRepository, logger);
    }

    private CompletableFuture<Void> load() {
        return CompletableFuture.runAsync(() -> {
            MapCollection mapCollection = mapManager.getMapCollection();

            try {
                mapCollection.load(ApConstants.ID);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load maps of namespace %s".formatted(ApConstants.ID), e);
            }
        });
    }
}
