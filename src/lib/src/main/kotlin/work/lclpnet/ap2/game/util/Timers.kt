package work.lclpnet.ap2.game.util

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.world.BossEvent
import work.lclpnet.ap2.api.util.action.Action
import work.lclpnet.ap2.ext.inWholeTicks
import work.lclpnet.ap2.ext.scheduler
import work.lclpnet.ap2.ext.translations
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.game.util.BossBarTimer
import work.lclpnet.kibu.hook.HookFactory
import kotlin.time.Duration

fun MiniGameInstance.addTimer(bossBar: BossEvent, duration: Duration): Action<Runnable?> {
    val onEnd = HookFactory.createArrayBacked(Runnable::class.java) { ops ->
        Runnable {
            for (op in ops) {
                op.run()
            }
        }
    }

    var timer = duration.inWholeTicks.toInt()

    scheduler.interval(1) { info ->
        if (timer-- <= 0) {
            info.cancel()
            bossBar.setProgress(0f)
            onEnd.invoker().run()
            return@interval
        }

        if (timer % 20 == 0) {
            bossBar.setProgress((timer.toFloat() / duration.inWholeTicks))
        }
    }

    return Action.create(onEnd)
}

@JvmOverloads
fun MiniGameInstance.createTimer(
    subject: Any,
    duration: Duration,
    color: BossEvent.BossBarColor = BossEvent.BossBarColor.RED,
): BossBarTimer {
    val timer = BossBarTimer.builder(translations, subject)
        .withAlertSound(false)
        .withColor(color)
        .withDurationTicks(duration.inWholeTicks.toInt())
        .build()

    timer.addPlayers(PlayerLookup.all(gameHandle.server))
    timer.start(gameHandle.bossBarProvider, gameHandle.scheduler)

    return timer
}
