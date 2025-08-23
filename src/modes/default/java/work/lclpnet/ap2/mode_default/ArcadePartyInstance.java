package work.lclpnet.ap2.mode_default;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import work.lclpnet.activity.manager.ActivityManager;
import work.lclpnet.ap2.api.base.GameQueue;
import work.lclpnet.ap2.api.base.MiniGameManager;
import work.lclpnet.ap2.api.config.Ap2Config;
import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.ap2.api.music.SongCache;
import work.lclpnet.ap2.impl.base.FabricMiniGameManager;
import work.lclpnet.ap2.impl.base.PlayerManagerImpl;
import work.lclpnet.ap2.impl.base.VotedGameQueue;
import work.lclpnet.ap2.impl.bootstrap.ApBootstrap;
import work.lclpnet.ap2.impl.game.PlayerUtil;
import work.lclpnet.ap2.impl.i18n.DynamicLanguageManager;
import work.lclpnet.ap2.impl.i18n.VanillaTranslations;
import work.lclpnet.ap2.impl.music.MapSongCache;
import work.lclpnet.ap2.mode_default.activity.PreparationActivity;
import work.lclpnet.ap2.mode_default.cmd.ForceGameCommand;
import work.lclpnet.ap2.mode_default.cmd.ScoreCommand;
import work.lclpnet.ap2.mode_default.util.ApBaseArgs;
import work.lclpnet.ap2.mode_default.util.ScoreManager;
import work.lclpnet.config.json.JsonConfigFactory;
import work.lclpnet.kibu.cmd.impl.CommandStack;
import work.lclpnet.kibu.hook.HookStack;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.game.api.GameEnvironment;
import work.lclpnet.lobby.game.api.GameInstance;
import work.lclpnet.lobby.game.api.option.GameOptions;
import work.lclpnet.lobby.game.api.option.VoteResult;
import work.lclpnet.translations.DefaultLanguageTranslator;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import static work.lclpnet.ap2.impl.util.ThreadUtil.onThread;

public class ArcadePartyInstance implements GameInstance {

    private static final int WIN_SCORE = 30;

    private final GameEnvironment environment;
    private final VanillaTranslations vanillaTranslations;
    private final JsonConfigFactory<Ap2Config> configFactory;
    private final Logger logger;

    public ArcadePartyInstance(GameEnvironment environment, VanillaTranslations vanillaTranslations,
                               JsonConfigFactory<Ap2Config> configFactory, Logger logger) {
        this.environment = environment;
        this.vanillaTranslations = vanillaTranslations;
        this.configFactory = configFactory;
        this.logger = logger;
    }

    @Override
    public void start(GameOptions options) {
        MinecraftServer server = environment.getServer();
        ApBootstrap bootstrap = new ApBootstrap(configFactory, logger);

        bootstrap.loadConfig(ForkJoinPool.commonPool())
                .thenCompose(configManager -> bootstrap.dispatch(configManager.getConfig(), environment, vanillaTranslations))
                .thenCompose(onThread(server, result -> {
                    dispatchGameStart(result, options);
                }))
                .exceptionally(throwable -> {
                    logger.error("Failed to load ArcadeParty2", throwable);
                    return null;
                });
    }

    private void dispatchGameStart(ApBootstrap.Result result, GameOptions options) {
        MinecraftServer server = environment.getServer();
        Translations translations = environment.getTranslations();

        MiniGameManager gameManager = new FabricMiniGameManager(logger);
        List<MiniGame> votedGames = getVotedGames(gameManager, options);
        GameQueue queue = new VotedGameQueue(gameManager, votedGames, 5);

        PlayerManagerImpl playerManager = new PlayerManagerImpl(server);
        PlayerUtil playerUtil = new PlayerUtil(server, playerManager);

        ScoreManager scoreManager = new ScoreManager(server.getPlayerManager(), WIN_SCORE);
        CommandStack commandStack = environment.getCommandStack();

        ForceGameCommand forceGameCommand = new ForceGameCommand(gameManager, queue::setNextGame);
        forceGameCommand.register(commandStack);

        ScoreCommand scoreCommand = new ScoreCommand(scoreManager, translations);
        scoreCommand.register(commandStack);

        HookStack hookStack = environment.getHookStack();
        initDynamicLanguages(hookStack, translations, server);

        ApMiniGameArgs container = new ApMiniGameArgs(server, logger, translations, hookStack,
                commandStack, environment.getSchedulerStack(), result.worldFacade(),
                result.mapFacade(), playerUtil, gameManager, result.songManager(), result.dataManager());

        SongCache songCache = new MapSongCache();

        var args = new ApBaseArgs(container, queue, playerManager, forceGameCommand, songCache, scoreManager,
                environment.getFinisher());

        PreparationActivity preparation = new PreparationActivity(args);

        ActivityManager.getInstance().startActivity(preparation);
    }

    private List<MiniGame> getVotedGames(MiniGameManager gameManager, GameOptions options) {
        Map<MiniGame, Integer> voted = options.getVotingResults(ArcadePartyDefaultGame.VOTING_MINI_GAMES, MiniGame.class)
                .map(VoteResult::asMap)
                .orElse(Map.of());

        Random random = new Random();

        return voted.keySet().stream()
                // only voted games
                .filter(game -> voted.getOrDefault(game, 0) > 0)
                // group by vote count
                .collect(Collectors.groupingBy(voted::get)).entrySet().stream()
                // sort by grouped vote count descending
                .sorted(Comparator.<Entry<Integer, List<MiniGame>>>comparingInt(Entry::getKey).reversed())
                .map(Entry::getValue)
                // shuffle order of games with the same vote count
                .flatMap(voteGroup -> {
                    Collections.shuffle(voteGroup, random);
                    return voteGroup.stream();
                })
                // voted games are not the same instances as the ones in the game manager, therefore lookup correct one
                .flatMap(game -> gameManager.getGame(game.getId()).stream())
                .toList();
    }

    private void initDynamicLanguages(HookStack hookStack, Translations translations, MinecraftServer server) {
        // translation reload is called off-thread by the DynamicLanguageManager
        var reloadLock = new Object();
        var callback = reload(translations, reloadLock);

        var manager = new DynamicLanguageManager(vanillaTranslations, translations::getLanguage, callback);

        manager.init(hookStack, PlayerLookup.all(server));
    }

    private Runnable reload(Translations translations, Object lock) {
        if (translations.getTranslator() instanceof DefaultLanguageTranslator translator) return () -> {
            synchronized (lock) {
                // only one reload should be done at once
                translator.reload().join();
            }
        };

        return () -> {};  // NOOP
    }
}
