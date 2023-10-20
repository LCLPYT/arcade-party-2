package work.lclpnet.ap2.base;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import work.lclpnet.activity.manager.ActivityManager;
import work.lclpnet.ap2.base.activity.PreparationActivity;
import work.lclpnet.ap2.impl.WorldFacadeImpl;
import work.lclpnet.kibu.plugin.hook.HookStack;
import work.lclpnet.lobby.game.GameEnvironment;
import work.lclpnet.lobby.game.GameInstance;
import work.lclpnet.lobby.game.GameStarter;
import work.lclpnet.lobby.game.map.MapCollection;
import work.lclpnet.lobby.game.map.MapManager;
import work.lclpnet.lobby.game.map.MapRepository;
import work.lclpnet.lobby.game.map.UrlMapRepository;
import work.lclpnet.lobby.game.start.ConditionGameStarter;
import work.lclpnet.lobby.util.WorldContainer;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class ArcadePartyGameInstance implements GameInstance {

    private final Logger logger = ArcadeParty.logger;
    private final GameEnvironment environment;
    private final MapManager mapManager;

    public ArcadePartyGameInstance(GameEnvironment environment) {
        this.environment = environment;

        try {
            this.mapManager = createMapManager();
        } catch (MalformedURLException e) {
            throw new RuntimeException("Failed to setup map repository url", e);
        }
    }

    @Override
    public GameStarter createStarter(GameStarter.Args args, GameStarter.Callback callback) {
        return new ConditionGameStarter(this::canStart, args, callback, environment);
    }

    private boolean canStart() {
        MinecraftServer server = environment.getServer();

        int players = PlayerLookup.all(server).size();

        if (ApConstants.DEVELOPMENT) {
            return players >= 1;
        }

        return players >= 2;
    }

    @Override
    public void start() {
        MinecraftServer server = environment.getServer();
        ArcadeParty arcadeParty = ArcadeParty.getInstance();

        WorldContainer worldContainer = new WorldContainer(server);
        arcadeParty.registerUnloadable(worldContainer);

        HookStack hookStack = environment.getHookStack();

        WorldFacadeImpl worldFacade = new WorldFacadeImpl(server, mapManager);
        worldFacade.init(hookStack);

        PreparationActivity preparation = new PreparationActivity(arcadeParty, worldFacade);

        load().thenRun(() ->
                        server.submit(() ->
                                ActivityManager.getInstance().startActivity(preparation)))
                .exceptionally(throwable -> {
                    logger.error("Failed to load ArcadeParty2", throwable);
                    return null;
                });
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

    private MapManager createMapManager() throws MalformedURLException {
        MapRepository mapRepository = new UrlMapRepository(Path.of("worlds").toUri().toURL(), logger);

        return new MapManager(mapRepository, logger);
    }
}
