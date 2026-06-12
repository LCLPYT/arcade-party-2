package work.lclpnet.ap2.game.util

import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.api.game.GameOverListener
import work.lclpnet.ap2.api.game.MiniGameResults
import work.lclpnet.ap2.api.game.data.*
import work.lclpnet.ap2.api.stats.GameSummary
import work.lclpnet.ap2.api.stats.StatsManager
import work.lclpnet.ap2.api.util.action.Action
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.impl.game.WinSequence
import work.lclpnet.ap2.impl.game.data.CombinedDataContainer
import work.lclpnet.ap2.impl.game.data.SupremeDataContainer
import work.lclpnet.game.map.GameMap
import work.lclpnet.game.util.ProtectorUtils
import work.lclpnet.kibu.hook.Hook
import work.lclpnet.kibu.hook.HookFactory
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Function
import java.util.stream.Collectors
import kotlin.concurrent.Volatile
import kotlin.time.Clock

class WinManager<T, Ref : SubjectRef>(
    private val gameHandle: MiniGameHandle,
    private val map: GameMap?,
    private val data: Data<T, Ref>
) {
    private val gameOverHook: Hook<GameOverListener> = HookFactory.createArrayBacked(
        GameOverListener::class.java
    ) { hooks ->
        GameOverListener {
            for (hook in hooks) {
                hook.onGameOver()
            }
        }
    }
    private val forcedWinners = SupremeDataContainer<T, Ref>(data.subjectRefs)

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
                data.container.copy()
            )
        )

        val result = data.winnersFactory.apply(finalData)
        val statsId = submitStats(result)
        val winSequence = WinSequence(gameHandle, finalData, data.playerRefs, result, status, statsId)

        return winSequence.start()
    }

    private fun submitStats(result: GenericGameResult<Ref>): CompletableFuture<Optional<UUID>> {
        val statsManager = this.statsManager ?: return CompletableFuture.completedFuture(Optional.empty())

        statsManager.fillDefaults(result)
        statsManager.freeze()

        val end = Clock.System.now()

        val initialParticipants = gameHandle.participants.initialParticipants

        val summary = GameSummary(
            gameHandle.gameInfo,
            map,
            gameHandle.startTime,
            end,
            initialParticipants
        )

        val stats = statsManager.getResult(summary, result)

        return gameHandle.submitStats(stats)
            .thenApply { Optional.of(it) }
            .exceptionally { err: Throwable ->
                gameHandle.logger.error("Failed to submit stats", err)
                Optional.empty<UUID>()
            }
    }

    fun addListener(listener: GameOverListener) {
        gameOverHook.register(listener)
    }

    fun checkForLastRemaining() {
        val participatingSubjects = gameHandle.participants.stream()
            .map(data.subjectMapper)
            .flatMap { it.stream() }
            .collect(Collectors.toSet())

        val size = participatingSubjects.size

        if (size > 1) return

        if (size == 1) {
            val lastRemaining = participatingSubjects.iterator().next()

            data.container.add(lastRemaining)
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

    @JvmRecord
    data class Data<T, Ref : SubjectRef>(
        val container: DataContainer<T, Ref>,
        val subjectMapper: Function<ServerPlayer, Optional<T>>,
        val subjectRefs: SubjectRefFactory<T, Ref>,
        val playerRefs: PlayerSubjectRefFactory<Ref>,
        val winnersFactory: Function<DataContainer<T, Ref>, GenericGameResult<Ref>>,
    )
}