package work.lclpnet.ap2.base;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import work.lclpnet.activity.manager.ActivityManager;
import work.lclpnet.ap2.api.base.GameQueue;
import work.lclpnet.ap2.api.base.MiniGameManager;
import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.ap2.api.util.music.SongCache;
import work.lclpnet.ap2.base.activity.PreparationActivity;
import work.lclpnet.ap2.base.cmd.ForceGameCommand;
import work.lclpnet.ap2.impl.base.PlayerManagerImpl;
import work.lclpnet.ap2.impl.base.SimpleMiniGameManager;
import work.lclpnet.ap2.impl.base.VotedGameQueue;
import work.lclpnet.ap2.impl.bootstrap.ApBootstrap;
import work.lclpnet.ap2.impl.game.PlayerUtil;
import work.lclpnet.ap2.impl.util.music.MapSongCache;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.lobby.game.api.GameEnvironment;
import work.lclpnet.lobby.game.api.GameInstance;
import work.lclpnet.lobby.game.api.GameStarter;
import work.lclpnet.lobby.game.start.ConditionGameStarter;

import java.util.List;
import java.util.concurrent.ForkJoinPool;

import static work.lclpnet.ap2.impl.util.FutureUtil.onThread;

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
        ApBootstrap bootstrap = new ApBootstrap(logger);

        bootstrap.loadConfig(ForkJoinPool.commonPool())
                .thenCompose(configManager -> bootstrap.dispatch(configManager.getConfig(), environment))
                .thenCompose(onThread(server, this::dispatchGameStart))
                .exceptionally(throwable -> {
                    logger.error("Failed to load ArcadeParty2", throwable);
                    return null;
                });
    }

    private void dispatchGameStart(ApBootstrap.Result result) {
        MinecraftServer server = environment.getServer();
        ArcadeParty arcadeParty = ArcadeParty.getInstance();

        TranslationService translationService = arcadeParty.getTranslationService();

        MiniGameManager gameManager = new SimpleMiniGameManager(logger);
        List<MiniGame> votedGames = List.of();  // TODO get from vote manager and shuffle
        GameQueue queue = new VotedGameQueue(gameManager, votedGames, 5);

        PlayerManagerImpl playerManager = new PlayerManagerImpl(server);
        PlayerUtil playerUtil = new PlayerUtil(server, playerManager);

        ForceGameCommand forceGameCommand = new ForceGameCommand(gameManager, queue::setNextGame);
        forceGameCommand.register(environment.getCommandStack());

        ApContainer container = new ApContainer(server, logger, translationService, environment.getHookStack(),
                environment.getCommandStack(), environment.getSchedulerStack(), result.worldFacade(),
                result.mapFacade(), playerUtil, gameManager, result.songManager(), result.dataManager());

        SongCache songCache = new MapSongCache();

        var args = new PreparationActivity.Args(arcadeParty, container, queue, playerManager, forceGameCommand, songCache);
        PreparationActivity preparation = new PreparationActivity(args);

        ActivityManager.getInstance().startActivity(preparation);
    }
}
