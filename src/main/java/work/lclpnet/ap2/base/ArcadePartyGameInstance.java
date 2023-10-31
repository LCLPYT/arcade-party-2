package work.lclpnet.ap2.base;

import it.unimi.dsi.fastutil.Pair;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import work.lclpnet.activity.manager.ActivityManager;
import work.lclpnet.ap2.api.base.GameQueue;
import work.lclpnet.ap2.api.base.MiniGameManager;
import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.ap2.api.map.MapFacade;
import work.lclpnet.ap2.api.map.MapRandomizer;
import work.lclpnet.ap2.base.activity.PreparationActivity;
import work.lclpnet.ap2.impl.base.PlayerManagerImpl;
import work.lclpnet.ap2.impl.base.SimpleMiniGameManager;
import work.lclpnet.ap2.impl.base.VotedGameQueue;
import work.lclpnet.ap2.impl.game.PlayerUtil;
import work.lclpnet.ap2.impl.map.BalancedMapRandomizer;
import work.lclpnet.ap2.impl.map.MapFacadeImpl;
import work.lclpnet.ap2.impl.map.SqliteAsyncMapFrequencyManager;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.lobby.game.api.GameEnvironment;
import work.lclpnet.lobby.game.api.GameInstance;
import work.lclpnet.lobby.game.api.GameStarter;
import work.lclpnet.lobby.game.api.WorldFacade;
import work.lclpnet.lobby.game.map.*;
import work.lclpnet.lobby.game.start.ConditionGameStarter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class ArcadePartyGameInstance implements GameInstance {

    private static final int MIN_REQUIRED_PLAYERS = 2;
    private final Logger logger = ArcadeParty.logger;
    private final GameEnvironment environment;

    public ArcadePartyGameInstance(GameEnvironment environment) {
        this.environment = environment;
    }

    @Override
    public GameStarter createStarter(GameStarter.Args args, GameStarter.Callback callback) {
        ConditionGameStarter starter = new ConditionGameStarter(this::canStart, args, callback, environment);

        ArcadeParty arcadeParty = ArcadeParty.getInstance();
        TranslationService translations = arcadeParty.getTranslationService();

        var msg = translations.translateText("lobby.game.not_enough_players", MIN_REQUIRED_PLAYERS)
                .formatted(Formatting.RED);

        starter.setConditionMessage(msg::translateFor);

        starter.setConditionBossBarValue(translations.translateText("lobby.game.waiting_for_players"));

        return starter;
    }

    private boolean canStart() {
        MinecraftServer server = environment.getServer();

        int players = PlayerLookup.all(server).size();

        if (ApConstants.DEVELOPMENT) {
            return players >= 1;
        }

        return players >= MIN_REQUIRED_PLAYERS;
    }

    @Override
    public void start() {
        MinecraftServer server = environment.getServer();

        MapManager mapManager = createMapManager();

        WorldFacade worldFacade = environment.getWorldFacade(() -> mapManager);
        var mapPair = createMapFacade(server, mapManager, worldFacade);

        MapFacade mapFacade = mapPair.left();
        var frequencyManager = mapPair.right();

        environment.closeWhenDone(() -> {
            try {
                frequencyManager.close();
            } catch (Exception e) {
                logger.error("Failed to close frequency manager", e);
            }
        });

        CompletableFuture.allOf(loadMaps(mapManager), loadSqlite(frequencyManager, server))
                .thenCompose(nil -> server.submit(() -> dispatchGameStart(server, worldFacade, mapFacade)))
                .exceptionally(throwable -> {
                    logger.error("Failed to load ArcadeParty2", throwable);
                    return null;
                });
    }

    private void dispatchGameStart(MinecraftServer server, WorldFacade worldFacade, MapFacade mapFacade) {
        ArcadeParty arcadeParty = ArcadeParty.getInstance();

        TranslationService translationService = arcadeParty.getTranslationService();

        MiniGameManager gameManager = new SimpleMiniGameManager(logger);
        List<MiniGame> votedGames = List.of();  // TODO get from vote manager and shuffle
        GameQueue queue = new VotedGameQueue(gameManager, votedGames, 5);

        PlayerUtil playerUtil = new PlayerUtil();

        ApContainer container = new ApContainer(server, logger, translationService, environment.getHookStack(),
                environment.getSchedulerStack(), worldFacade, mapFacade, playerUtil);

        PlayerManagerImpl playerManager = new PlayerManagerImpl(server);

        var args = new PreparationActivity.Args(arcadeParty, container, queue, playerManager);
        PreparationActivity preparation = new PreparationActivity(args);

        ActivityManager.getInstance().startActivity(preparation);
    }

    private CompletableFuture<Void> loadSqlite(SqliteAsyncMapFrequencyManager frequencyManager, MinecraftServer server) {
        return frequencyManager.open(server)
                .exceptionally(throwable -> {
                    logger.error("Failed to establish sqlite connection. Fallback to StubMapFrequencyManager", throwable);
                    return null;
                });
    }

    @NotNull
    private static CompletableFuture<Void> loadMaps(MapManager mapManager) {
        return CompletableFuture.runAsync(() -> {
            // load mini game maps

            try {
                // load general arcade party 2 maps
                mapManager.loadAll(new MapDescriptor(ApConstants.ID, "", ApConstants.MAP_VERSION));
            } catch (IOException e) {
                throw new RuntimeException("Failed to load maps of namespace %s".formatted(ApConstants.ID), e);
            }
        });
    }

    @NotNull
    private Pair<MapFacade, SqliteAsyncMapFrequencyManager> createMapFacade(
            MinecraftServer server, MapManager mapManager, WorldFacade worldFacade) {

        Path dbPath = Path.of("config")
                .resolve(ApConstants.ID)
                .resolve("map_frequencies.sqlite");

        SqliteAsyncMapFrequencyManager frequencyTracker = new SqliteAsyncMapFrequencyManager(dbPath, logger);

        MapRandomizer mapRandomizer = new BalancedMapRandomizer(mapManager, frequencyTracker, new Random());

        MapFacade mapFacade = new MapFacadeImpl(worldFacade, mapRandomizer, server, logger);

        return Pair.of(mapFacade, frequencyTracker);
    }

    private MapManager createMapManager() {
        MapRepository mapRepository;

        mapRepository = new UriMapRepository(Path.of("worlds").toUri(), logger);

        var lookup = new RepositoryMapLookup(mapRepository);

        return new MapManager(lookup);
    }
}
