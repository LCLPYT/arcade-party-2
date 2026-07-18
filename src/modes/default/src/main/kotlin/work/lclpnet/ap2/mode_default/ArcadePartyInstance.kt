package work.lclpnet.ap2.mode_default

import kotlinx.coroutines.*
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.SharedConstants
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger
import work.lclpnet.activity.Activity
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.impl.base.GameQueue
import work.lclpnet.ap2.impl.base.MiniGameManager
import work.lclpnet.ap2.api.config.Ap2Config
import work.lclpnet.ap2.api.stats.SessionStatsRecorder
import work.lclpnet.ap2.game.MiniGame
import work.lclpnet.ap2.game.color.SessionColorPreferences
import work.lclpnet.ap2.game.player.PlayerManagerImpl
import work.lclpnet.ap2.game.util.PlayerUtil
import work.lclpnet.ap2.impl.base.FabricMiniGameManager
import work.lclpnet.ap2.impl.base.VotedGameQueue
import work.lclpnet.ap2.impl.bootstrap.ApBootstrap
import work.lclpnet.ap2.impl.i18n.DynamicLanguageManager
import work.lclpnet.ap2.impl.i18n.VanillaTranslations
import work.lclpnet.ap2.impl.music.MapSongCache
import work.lclpnet.ap2.mode_default.activity.PreparationActivity
import work.lclpnet.ap2.mode_default.cmd.ForceGameCommand
import work.lclpnet.ap2.mode_default.cmd.ScoreCommand
import work.lclpnet.ap2.mode_default.util.ApBaseArgs
import work.lclpnet.ap2.mode_default.util.ScoreManager
import work.lclpnet.ap2.util.FontService
import work.lclpnet.ap2.util.MinecraftDispatcher
import work.lclpnet.ap2.util.ServerViewDistanceManager
import work.lclpnet.ap2.util.TablistManager
import work.lclpnet.config.json.JsonConfigFactory
import work.lclpnet.gaco.ds.queue.JsonFileQueuePersistence
import work.lclpnet.game.api.GameEnvironment
import work.lclpnet.game.api.GameInstance
import work.lclpnet.game.api.option.VoteResult
import work.lclpnet.kibu.assets.AssetManager
import work.lclpnet.kibu.hook.HookStack
import work.lclpnet.kibu.translate.Translations
import work.lclpnet.translations.DefaultLanguageTranslator
import java.lang.Runnable

private const val WIN_SCORE = 30

class ArcadePartyInstance(
    private val environment: GameEnvironment,
    private val vanillaTranslations: VanillaTranslations,
    private val configFactory: JsonConfigFactory<Ap2Config>,
    private val miniGameVoteResult: VoteResult<MiniGame>,
    private val logger: Logger
) : GameInstance {

    private val fontService: FontService
    private val scope = CoroutineScope(MinecraftDispatcher(environment.server) + SupervisorJob())

    init {
        val assetManager = AssetManager.getShared(SharedConstants.getCurrentVersion().name())

        this.fontService = FontService(assetManager, logger)

        environment.whenDone { scope.cancel() }
    }

    override fun start() {
        val bootstrap = ApBootstrap(configFactory, logger) { action ->
            environment.whenDone(action)
        }

        scope.launch {
            try {
                val configManager = bootstrap.loadConfig()

                val result = bootstrap.dispatch(
                    configManager.config,
                    environment,
                    vanillaTranslations,
                    fontService
                )

                setupMode(result)
            } catch (t: Throwable) {
                logger.error("Failed to load ArcadeParty2", t)
            }
        }
    }

    private suspend fun setupMode(result: ApBootstrap.Result) {
        val gameManager: MiniGameManager = FabricMiniGameManager(logger)

        val queue = withContext(Dispatchers.IO) {
            createGameQueue(gameManager)
        }

        // the enclosing coroutine runs on MinecraftDispatcher, so this resumes on the server thread
        dispatchGameStart(result, gameManager, queue)
    }

    private fun createGameQueue(gameManager: MiniGameManager): GameQueue {
        val votedGames = getVotedGames(gameManager)

        val gameQueuePersistence = JsonFileQueuePersistence.create(
            ApConstants.RUNTIME_CONFIG_ID,
            ApConstants.identifier("game_queue"),
            gameManager.gameCodec,
            logger
        )

        val miniGames = gameManager.games
        val minQueueSize = Math.clamp(miniGames.size.toLong(), 1, 10)

        return VotedGameQueue(miniGames, votedGames, minQueueSize, gameQueuePersistence)
    }

    private fun dispatchGameStart(result: ApBootstrap.Result, gameManager: MiniGameManager, queue: GameQueue) {
        val server = environment.server
        val translations = environment.translations

        val playerManager = PlayerManagerImpl(server)
        val playerUtil = PlayerUtil(server, playerManager)

        val colorPreferences = SessionColorPreferences()

        val scoreManager = ScoreManager(server.playerList, WIN_SCORE)
        val commandStack = environment.commandStack

        val forceGameCommand = ForceGameCommand(gameManager) { miniGame ->
            queue.setNextGame(miniGame)
        }

        forceGameCommand.register(commandStack)

        val scoreCommand = ScoreCommand(scoreManager, translations)
        scoreCommand.register(commandStack)

        val hookStack = environment.hookStack
        initDynamicLanguages(hookStack, translations, server)

        val container = ApMiniGameArgs(
            server,
            logger,
            translations,
            hookStack,
            commandStack,
            environment.schedulerStack,
            result.worldFacade,
            result.mapFacade,
            playerUtil,
            gameManager,
            result.songManager,
            result.dataManager,
            fontService
        )

        val songCache = MapSongCache()

        val sessionStats = SessionStatsRecorder(translations, logger)
        sessionStats.init(hookStack)

        val tablistManager = TablistManager(translations, server)

        val viewDistanceManager = ServerViewDistanceManager(server)
        viewDistanceManager.reset()

        val args = ApBaseArgs(
            miniGameArgs = container,
            gameQueue = queue,
            playerManager = playerManager,
            colorPreferences = colorPreferences,
            forceGameCommand = forceGameCommand,
            sharedSongCache = songCache,
            scoreManager = scoreManager,
            finisher = environment.finisher,
            stats = sessionStats,
            tablistManager = tablistManager,
            assetManager = result.assetManager,
            activitySwitcher = { activity: Activity ->
                environment.switchRootActivity(activity)
            },
            viewDistanceManager = viewDistanceManager,
        )

        val preparation = PreparationActivity(args)

        environment.switchRootActivity(preparation)
    }

    private fun getVotedGames(gameManager: MiniGameManager): List<MiniGame> {
        val voted = miniGameVoteResult.asMap()

        return voted.keys
            .filter { voted.getOrDefault(it, 0) > 0 }  // only voted games
            .groupBy { voted[it] ?: 0 }  // group by vote count
            .entries
            .sortedBy { it.key }  // sort by grouped vote count descending
            .reversed()
            .flatMap { it.value.shuffled() }  // shuffle order of games with the same vote count
            .mapNotNull { gameManager.getGame(it.id) }
    }

    private fun initDynamicLanguages(hookStack: HookStack, translations: Translations, server: MinecraftServer) {
        // translation reload is called off-thread by the DynamicLanguageManager
        val reloadLock = Any()
        val callback = reload(translations, reloadLock)

        val manager = DynamicLanguageManager(
            vanillaTranslations,
            { player -> translations.getLanguage(player) },
            callback
        )

        manager.init(hookStack, PlayerLookup.all(server))
    }

    private fun reload(translations: Translations, lock: Any): Runnable {
        val translator = translations.translator

        if (translator is DefaultLanguageTranslator) return {
            synchronized(lock) {
                // only one reload should be done at once
                translator.reload().join()
            }
        }

        return {}  // NOOP
    }
}
