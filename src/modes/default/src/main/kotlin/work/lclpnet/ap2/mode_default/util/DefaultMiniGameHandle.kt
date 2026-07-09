package work.lclpnet.ap2.mode_default.util

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.border.WorldBorder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import work.lclpnet.activity.util.BossBarHandler
import work.lclpnet.ap2.api.base.WorldBorderManager
import work.lclpnet.ap2.api.data.DataManager
import work.lclpnet.ap2.api.game.GameType
import work.lclpnet.ap2.api.game.MiniGameResults
import work.lclpnet.ap2.api.map.MapFacade
import work.lclpnet.ap2.api.music.SongCache
import work.lclpnet.ap2.api.music.SongManager
import work.lclpnet.ap2.api.stats.StatsResult
import work.lclpnet.ap2.core.type.ApServerPlayerEntity
import work.lclpnet.ap2.game.GameInfo
import work.lclpnet.ap2.game.MiniGame
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.color.PlayerColorPreferences
import work.lclpnet.ap2.game.player.Participants
import work.lclpnet.ap2.game.player.PlayerRankView
import work.lclpnet.ap2.game.team.TeamConfig
import work.lclpnet.ap2.game.util.PlayerUtil
import work.lclpnet.ap2.impl.i18n.GameScopedTranslator
import work.lclpnet.ap2.impl.util.DeathMessages
import work.lclpnet.ap2.impl.util.world.SubWorldManager
import work.lclpnet.ap2.mode_default.activity.MiniGameActivity
import work.lclpnet.ap2.mode_default.activity.PreparationActivity
import work.lclpnet.ap2.util.AssetManager
import work.lclpnet.ap2.util.FontService
import work.lclpnet.ap2.util.TablistManager
import work.lclpnet.ap2.util.scoreboard.CustomScoreboardManager
import work.lclpnet.game.api.WorldFacade
import work.lclpnet.game.impl.WorldContainer
import work.lclpnet.game.impl.prot.BasicProtector
import work.lclpnet.game.impl.prot.MutableProtectionConfig
import work.lclpnet.game.util.ProtectorUtils
import work.lclpnet.kibu.cmd.type.CommandRegistrar
import work.lclpnet.kibu.hook.HookStack
import work.lclpnet.kibu.scheduler.api.TaskScheduler
import work.lclpnet.kibu.scheduler.util.SchedulerStack
import work.lclpnet.kibu.translate.Translations
import work.lclpnet.kibu.translate.bossbar.BossBarProvider
import work.lclpnet.notica.Notica
import work.lclpnet.notica.api.SongHandle
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import kotlin.time.Clock
import kotlin.time.Instant

class DefaultMiniGameHandle(
    private val game: MiniGame,
    private val args: ApBaseArgs,
    override val bossBarProvider: BossBarProvider,
    override val bossBarHandler: BossBarHandler,
    override val scoreboardManager: CustomScoreboardManager,
    private val remake: AtomicBoolean,
    override val rankView: PlayerRankView,
) : MiniGameHandle, WorldBorderManager {

    override val logger: Logger = LoggerFactory.getLogger(game.id.toString())

    private var protectionConfig: MutableProtectionConfig? = null

    @Volatile
    private var protector: BasicProtector? = null

    @Volatile
    private var whenDoneActions: MutableList<Runnable>? = null

    private var ended = false

    @Volatile
    private var statsId: CompletableFuture<UUID>? = null

    @Volatile
    private var subWorldManagerInstance: SubWorldManager? = null

    @Volatile
    private var worldContainer: WorldContainer? = null

    private var world: ServerLevel? = null

    private var startTimeOrNull: Instant? = null

    override lateinit var rootScheduler: TaskScheduler
        private set

    fun init() {
        val container = args.miniGameArgs

        container.hookStack.push()
        container.commandStack.push()
        container.schedulerStack.push()

        rootScheduler = container.schedulerStack.current()

        container.schedulerStack.push()

        setStartTime()
    }

    fun setStartTime() {
        startTimeOrNull = Clock.System.now()
    }

    override val server: MinecraftServer
        get() = args.miniGameArgs.server

    override val gameInfo: GameInfo
        get() = game

    override val worldFacade: WorldFacade
        get() = args.miniGameArgs.worldFacade

    override val mapFacade: MapFacade
        get() = args.miniGameArgs.mapFacade

    override val hooks: HookStack
        get() = args.miniGameArgs.hookStack

    override val commands: CommandRegistrar
        get() = args.miniGameArgs.commandStack

    override val scheduler: SchedulerStack
        get() = args.miniGameArgs.schedulerStack

    override val translations: Translations by lazy {
        GameScopedTranslator.scope(args.miniGameArgs.translations, game.titleKey)
    }

    override val participants: Participants
        get() = args.playerManager

    override val colorPreferences: PlayerColorPreferences
        get() = args.colorPreferences

    override val worldBorderManager: WorldBorderManager
        get() = this

    override val playerUtil: PlayerUtil
        get() = args.miniGameArgs.playerUtil

    override val teamConfig: Optional<TeamConfig>
        get() = Optional.empty()

    override val songManager: SongManager
        get() = args.miniGameArgs.songManager

    override val sharedSongCache: SongCache
        get() = args.sharedSongCache

    override val deathMessages: DeathMessages by lazy { DeathMessages(translations) }

    override val dataManager: DataManager
        get() = args.miniGameArgs.dataManager

    override val subWorldManager: SubWorldManager
        get() {
            subWorldManagerInstance?.let { return it }

            val container: WorldContainer
            val manager: SubWorldManager

            synchronized(this) {
                subWorldManagerInstance?.let { return it }

                val repo = mapFacade.assetRepository
                val server = this.server

                container = WorldContainer(server)
                manager = SubWorldManager(repo, server, container, logger)

                worldContainer = container
                subWorldManagerInstance = manager
            }

            container.init()
            manager.init(hooks)

            return manager
        }

    override val tablistManager: TablistManager
        get() = args.tablistManager

    override val assetManager: AssetManager
        get() = args.assetManager

    override val fontService: FontService
        get() = args.miniGameArgs.fontService

    override val startTime: Instant
        get() = checkNotNull(startTimeOrNull) { "Start time not set" }

    override fun resetGameScheduler() {
        val stack = scheduler

        stack.pop()
        stack.push()
    }

    @Synchronized
    override fun protect(action: Consumer<MutableProtectionConfig>) {
        if (protector == null) {
            synchronized(this) {
                if (protector == null) {
                    val config = MutableProtectionConfig()
                    protectionConfig = config
                    protector = BasicProtector(config)
                }
            }
        }

        val protector = this.protector!!
        val protectionConfig = this.protectionConfig!!

        protector.deactivate()

        ProtectorUtils.allowCreativeOperatorBypass(protectionConfig)
        action.accept(protectionConfig)

        protector.activate()
    }

    override fun whenDone(action: Runnable) {
        if (whenDoneActions == null) {
            synchronized(this) {
                if (whenDoneActions == null) {
                    whenDoneActions = ArrayList()
                }
            }
        }

        whenDoneActions!!.add(action)
    }

    @Synchronized
    override fun complete(results: MiniGameResults) {
        if (ended) return
        ended = true

        if (remake.get()) {
            val activity = MiniGameActivity(game, args)
            args.activitySwitcher.switchTo(activity)
            return
        }

        adjustScores(results)

        args.gameQueue.updateHistory(game)

        val activity = PreparationActivity(args)
        args.activitySwitcher.switchTo(activity)
    }

    private fun adjustScores(results: MiniGameResults) {
        val scoreManager = args.scoreManager
        val entriesByRank = results.entriesByRank

        when (game.type) {
            GameType.FFA, GameType.TOURNAMENT -> {
                // track player scores; 3 points for 1st, 2 points for 2nd, 1 point for 3rd
                var score = 3

                for (group in entriesByRank) {
                    if (score <= 0) break

                    for (playerResult in group) {
                        scoreManager.addScore(playerResult.ref, score)
                    }

                    score -= group.size
                }
            }
            GameType.TEAM -> {
                if (entriesByRank.isNotEmpty()) {
                    // members of the winning team get 3 points each
                    val winners = entriesByRank.first()

                    for (winner in winners) {
                        scoreManager.addScore(winner.ref, 3)
                    }
                }
            }
            GameType.BOUNTY -> {}
        }
    }

    fun unload() {
        val container = args.miniGameArgs

        container.hookStack.pop()
        container.commandStack.pop()

        val schedulerStack = container.schedulerStack
        schedulerStack.pop()  // game scheduler
        schedulerStack.pop()  // parent scheduler

        protector?.unload()

        whenDoneActions?.let { actions ->
            actions.forEach(Runnable::run)
            actions.clear()
        }

        Notica.getInstance(server).playingSongs.forEach(SongHandle::stop)

        for (player in PlayerLookup.all(server)) {
            (player as ApServerPlayerEntity).`ap2$setPlayerListName`(null)
        }

        playerUtil.updatePlayerListNames()

        worldContainer?.unload()
    }

    override fun getWorldBorder(): WorldBorder =
        checkNotNull(world) { "World is not set yet" }.worldBorder

    override fun setWorld(world: ServerLevel) {
        this.world = world
    }

    override val isFinale: Boolean
        get() = args.playerManager.isFinale

    override fun submitStats(stats: StatsResult): CompletableFuture<UUID> {
        statsId?.let { return it }

        synchronized(this) {
            statsId?.let { return it }

            // in the future, the stats id should be allocated by the stats backend
            // for now, just generate an id ourselves

            return CompletableFuture.completedFuture(UUID.randomUUID())
                .whenComplete { id, err ->
                    if (err != null) {
                        args.miniGameArgs.logger.error("Failed to submit stats", err)
                        return@whenComplete
                    }

                    args.stats.record(id, stats)
                }
                .also { statsId = it }
        }
    }
}
