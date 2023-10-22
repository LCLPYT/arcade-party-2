package work.lclpnet.ap2.base;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import work.lclpnet.activity.manager.ActivityManager;
import work.lclpnet.ap2.base.activity.PreparationActivity;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.lobby.game.api.GameEnvironment;
import work.lclpnet.lobby.game.api.GameInstance;
import work.lclpnet.lobby.game.api.GameStarter;
import work.lclpnet.lobby.game.api.WorldFacade;
import work.lclpnet.lobby.game.map.MapCollection;
import work.lclpnet.lobby.game.map.MapManager;
import work.lclpnet.lobby.game.map.MapRepository;
import work.lclpnet.lobby.game.map.UrlMapRepository;
import work.lclpnet.lobby.game.start.ConditionGameStarter;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class ArcadePartyGameInstance implements GameInstance {

    private final Logger logger = ArcadeParty.logger;
    private final GameEnvironment environment;

    public ArcadePartyGameInstance(GameEnvironment environment) {
        this.environment = environment;
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

        MapManager mapManager = createMapManager();

        load(mapManager)
                .thenCompose(nil -> server.submit(() -> {
                    WorldFacade worldFacade = environment.getWorldFacade(() -> mapManager);

                    TranslationService translationService = arcadeParty.getTranslationService();
                    PreparationActivity preparation = new PreparationActivity(arcadeParty, worldFacade, translationService);

                    ActivityManager.getInstance().startActivity(preparation);
                }))
                .exceptionally(throwable -> {
                    logger.error("Failed to load ArcadeParty2", throwable);
                    return null;
                });
    }

    private CompletableFuture<Void> load(MapManager mapManager) {
        return CompletableFuture.runAsync(() -> {
            MapCollection mapCollection = mapManager.getMapCollection();

            try {
                // load arcade party 2 maps
                mapCollection.load(ApConstants.ID);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load maps of namespace %s".formatted(ApConstants.ID), e);
            }
        });
    }

    private MapManager createMapManager() {
        MapRepository mapRepository;

        try {
            mapRepository = new UrlMapRepository(Path.of("worlds").toUri().toURL(), logger);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        return new MapManager(mapRepository, logger);
    }
}
