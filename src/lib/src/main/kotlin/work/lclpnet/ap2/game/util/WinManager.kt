package work.lclpnet.ap2.game.util

import net.minecraft.SharedConstants
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.api.game.GameOverListener
import work.lclpnet.ap2.api.game.MiniGameResults
import work.lclpnet.ap2.api.game.data.*
import work.lclpnet.ap2.api.stats.GameSummary
import work.lclpnet.ap2.api.stats.LevelInfo
import work.lclpnet.ap2.api.stats.StatsManager
import work.lclpnet.ap2.api.util.action.Action
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.data.CombinedDataContainer
import work.lclpnet.ap2.game.data.SupremeDataContainer
import work.lclpnet.game.util.ProtectorUtils
import work.lclpnet.kibu.hook.Hook
import work.lclpnet.kibu.hook.HookFactory
import work.lclpnet.kibu.translate.text.TranslatedText
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.Volatile
import kotlin.time.Clock

class WinManager<T, Ref : SubjectRef>(
    private val gameHandle: MiniGameHandle,
    private val levelInfo: LevelInfo,
    private val data: Data<T, Ref>
) : WinManagerAccess {
    private val gameOverHook: Hook<GameOverListener> = HookFactory.createArrayBacked(
        GameOverListener::class.java
    ) { hooks ->
        GameOverListener {
            for (hook in hooks) {
                hook.onGameOver()
            }
        }
    }
    private val forcedWinners = SupremeDataContainer(data.subjectRefs)

    @Volatile
    var gameOver = false
        private set

    private var statsManager: StatsManager<Ref>? = null

    fun complete(): Action<Runnable> {
        return startWinSequence(MiniGameResults.Status.SUCCESS)
    }

    fun cancel(): Action<Runnable> {
        return startWinSequence(MiniGameResults.Status.CANCELLED)
    }

    fun setStatsManager(statsManager: StatsManager<Ref>) {
        this.statsManager = statsManager
    }

    @Synchronized
    private fun startWinSequence(status: MiniGameResults.Status): Action<Runnable> {
        if (this.gameOver) return Action.noop()

        gameOver = true
        gameOverHook.invoker().onGameOver()

        gameHandle.resetGameScheduler()

        gameHandle.protect { config ->
            config.disallowAll()
            ProtectorUtils.allowCreativeOperatorBypass(config)
        }

        // concat forced winners, then the rest
        val finalData = CombinedDataContainer(
            listOf(
                forcedWinners,
                data.container().copy()
            )
        )

        val result = data.winnersFactory(finalData)
        val statsId = submitStats(result, finalData)
        val winSequence = WinSequence(gameHandle, finalData, data.playerRefs, result, status, statsId)

        return winSequence.start()
    }

    private fun submitStats(result: GenericGameResult<Ref>, finalData: DataContainer<T, Ref>): CompletableFuture<Optional<UUID>> {
        val statsManager = this.statsManager ?: return CompletableFuture.completedFuture(Optional.empty())

        statsManager.fillDefaults(result)
        statsManager.freeze()

        val end = Clock.System.now()

        val initialParticipants = gameHandle.participants.initialParticipants

        val summary = GameSummary(
            gameHandle.gameInfo,
            SharedConstants.getCurrentVersion().name(),
            levelInfo,
            gameHandle.startTime,
            end,
            initialParticipants
        )

        val details = buildScoreDetails(result, finalData)

        val stats = statsManager.getResult(summary, result, details)

        return gameHandle.submitStats(stats)
            .thenApply { Optional.of(it) }
            .exceptionally { err: Throwable ->
                gameHandle.logger.error("Failed to submit stats", err)
                Optional.empty<UUID>()
            }
    }

    private fun buildScoreDetails(
        result: GenericGameResult<Ref>,
        finalData: DataContainer<T, Ref>
    ): Map<Ref, TranslatedText> {
        val translations = gameHandle.translations
        val details = HashMap<Ref, TranslatedText>()

        for (rank in result.subjectResults) {
            val ref = rank.left() ?: continue
            val detail = finalData.getEntry(ref)?.toText(translations) ?: continue
            details[ref] = detail
        }

        return details
    }

    fun addListener(listener: GameOverListener) {
        gameOverHook.register(listener)
    }

    fun checkForLastRemaining() {
        val participatingSubjects = gameHandle.participants
            .mapNotNull(data.subjectMapper)
            .toSet()

        val size = participatingSubjects.size

        if (size > 1) return

        if (size == 1) {
            val lastRemaining = participatingSubjects.iterator().next()

            data.container().add(lastRemaining)
        }

        complete()
    }

    fun forceWin(winners: Set<T>) {
        forcedWinners.clear()

        winners.forEach { subject: T ->
            forcedWinners.add(subject)
        }

        complete()
    }

    override fun draw() {
        data.container().clear()
        complete()
    }

    override fun win(player: ServerPlayer) {
        val subject = data.subjectMapper(player)

        if (subject != null) {
            forceWin(setOf(subject))
        } else {
            draw()
        }
    }

    override fun win(players: Set<ServerPlayer>) {
        val subjects = players
            .mapNotNull(data.subjectMapper)
            .toSet()

        forceWin(subjects)
    }

    data class Data<T, Ref : SubjectRef>(
        val container: () -> DataContainer<T, Ref>,
        val subjectMapper: (ServerPlayer) -> T?,
        val subjectRefs: SubjectRefFactory<T, Ref>,
        val playerRefs: PlayerSubjectRefFactory<Ref?>,
        val winnersFactory: (DataContainer<T, Ref>) -> GenericGameResult<Ref>,
    )
}