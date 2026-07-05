package work.lclpnet.ap2.game.util

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.ext.sendGo
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.util.SubtitleCountdown
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The shared start sequence of a mini-game: an initial countdown followed by a number of optional
 * startup phases, after which the game begins.
 *
 * Phases registered via [beforeGo] form a continuation pipeline. Each phase receives a continuation
 * that it must invoke once it is done, which allows phases to perform asynchronous work (e.g. timers
 * or tutorials) before the game starts. The first registered phase runs first.
 *
 * This is provided as a composable alternative to inheritance, so that any [work.lclpnet.ap2.game.MiniGameInstance] can
 * reuse the sequence without extending a specific base class.
 */
class GameStartSequence(
    private val gameHandle: MiniGameHandle,
    private val players: () -> Collection<ServerPlayer> = { PlayerLookup.all(gameHandle.server) },
) {
    private val phases = ArrayDeque<StartupPhase>()
    var extraDelay = 0.seconds

    fun interface StartupPhase {
        /** Perform work, then invoke [next] to continue the sequence (synchronously or asynchronously). */
        fun run(next: Runnable)
    }

    /** Insert custom logic between the countdown and the game start. */
    fun beforeGo(phase: StartupPhase) = apply { phases.addLast(phase) }

    val initialDelay: Duration
        get() = PlayerUtil.getLoadingDelay(gameHandle.participants.asSet.size) +
                extraDelay.coerceAtLeast(0.seconds)

    /** Runs the initial countdown, then the registered phases, then invokes [onComplete]. */
    fun start(onComplete: Runnable) {
        SubtitleCountdown(
            gameHandle.server,
            gameHandle.scheduler,
            { },
            players
        ).schedule(initialDelay) {
            runPhases(onComplete)
        }
    }

    /** Like [start], but sends the "go" title and sound to every player right before invoking [onGo]. */
    fun startWithGo(onGo: Runnable) = start {
        for (player in players()) {
            gameHandle.sendGo(player)
        }

        onGo.run()
    }

    private fun runPhases(terminal: Runnable) {
        var next = terminal

        for (phase in phases.reversed()) {
            val continuation = next
            next = Runnable { phase.run(continuation) }
        }

        next.run()
    }
}