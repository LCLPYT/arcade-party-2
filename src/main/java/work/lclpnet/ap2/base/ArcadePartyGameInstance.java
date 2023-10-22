package work.lclpnet.ap2.base;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import work.lclpnet.activity.manager.ActivityManager;
import work.lclpnet.ap2.api.base.GameQueue;
import work.lclpnet.ap2.api.base.MiniGameManager;
import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.ap2.base.activity.PreparationActivity;
import work.lclpnet.ap2.impl.base.SimpleMiniGameManager;
import work.lclpnet.ap2.impl.base.VotedGameQueue;
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
import java.util.List;
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

        MapManager mapManager = createMapManager();

        load(mapManager)
                .thenCompose(nil -> server.submit(() -> dispatchGameStart(mapManager)))
                .exceptionally(throwable -> {
                    logger.error("Failed to load ArcadeParty2", throwable);
                    return null;
                });
    }

    private void dispatchGameStart(MapManager mapManager) {
        WorldFacade worldFacade = environment.getWorldFacade(() -> mapManager);
        ArcadeParty arcadeParty = ArcadeParty.getInstance();

        TranslationService translationService = arcadeParty.getTranslationService();

        MiniGameManager gameManager = new SimpleMiniGameManager(logger);
        List<MiniGame> votedGames = List.of();  // TODO get from vote manager and shuffle
        GameQueue queue = new VotedGameQueue(gameManager, votedGames, 5);

        PreparationActivity preparation = new PreparationActivity(arcadeParty, worldFacade, translationService, queue);

        ActivityManager.getInstance().startActivity(preparation);
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
