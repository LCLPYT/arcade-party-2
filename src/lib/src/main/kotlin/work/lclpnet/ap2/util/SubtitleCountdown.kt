package work.lclpnet.ap2.util

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.ext.inWholeTicks
import work.lclpnet.kibu.scheduler.api.RunningTask
import work.lclpnet.kibu.scheduler.api.TaskHandle
import work.lclpnet.kibu.scheduler.api.TaskScheduler
import kotlin.math.min
import kotlin.time.Duration

class SubtitleCountdown(
    val server: MinecraftServer,
    val scheduler: TaskScheduler,
    private val timerAction: (Int) -> Unit = {},
    val players: () -> Collection<ServerPlayer>,
) {
    private var time = 0
    private var seconds = 0
    private var task: TaskHandle? = null

    fun schedule(duration: Duration, onComplete: Runnable) {
        schedule(duration.inWholeTicks.toInt(), onComplete)
    }

    @Synchronized
    fun schedule(ticks: Int, onComplete: Runnable): TaskHandle {
        task?.cancel()
        task = null

        val seconds = min(3, ticks / 20)

        this.time = 0
        this.seconds = seconds

        val task = if (seconds <= 0) {
            scheduler.timeout(ticks.coerceAtLeast(0), onComplete)
        } else {
            scheduler.interval(1, ticks - seconds * 20L) { task ->
                tick(task)
            }.whenComplete {
                clearCountdown()
                onComplete.run()
            }
        }

        this.task = task

        return task
    }

    private fun tick(task: RunningTask) {
        val time: Int = time++

        if (time % 20 != 0) return

        if (seconds <= 0) {
            task.cancel()
            return
        }

        val color = when (seconds) {
            3 -> ChatFormatting.RED
            2 -> ChatFormatting.GOLD
            1 -> ChatFormatting.YELLOW
            else -> ChatFormatting.GREEN
        }

        val msg = Component.literal((seconds--).toString()).withStyle(color, ChatFormatting.BOLD)

        for (player in players()) {
            player.sendOverlayMessage(msg)
        }

        timerAction(seconds)
    }

    private fun clearCountdown() {
        for (player in players()) {
            player.sendOverlayMessage(Component.empty())
        }
    }
}