package work.lclpnet.ap2.game.util

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.ChatFormatting
import net.minecraft.world.BossEvent
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.util.action.Action
import work.lclpnet.ap2.ext.scheduler
import work.lclpnet.ap2.ext.server
import work.lclpnet.ap2.ext.translations
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedPlayerBossBar
import work.lclpnet.game.util.BossBarTimer
import work.lclpnet.kibu.hook.HookFactory
import work.lclpnet.kibu.scheduler.Ticks
import work.lclpnet.kibu.translate.bossbar.TranslatedBossBar

fun MiniGameInstance.addTimer(bossBar: BossEvent, durationSeconds: Int): Action<Runnable?> {
    return addTimerTicks(bossBar, durationSeconds * 20)
}

fun MiniGameInstance.addTimerTicks(bossBar: BossEvent, durationTicks: Int): Action<Runnable?> {
    val onEnd = HookFactory.createArrayBacked(Runnable::class.java) { ops ->
        Runnable {
            for (op in ops) {
                op.run()
            }
        }
    }

    var timer = durationTicks

    scheduler.interval(1) { info ->
        if (timer-- <= 0) {
            info.cancel()
            bossBar.setProgress(0f)
            onEnd.invoker().run()
            return@interval
        }

        if (timer % 20 == 0) {
            bossBar.setProgress((timer.toFloat() / durationTicks))
        }
    }

    return Action.create(onEnd)
}

fun MiniGameInstance.createTimer(subject: Any, durationSeconds: Int): BossBarTimer {
    return createTimer(subject, durationSeconds, BossEvent.BossBarColor.RED)
}

fun MiniGameInstance.createTimer(subject: Any, durationSeconds: Int, color: BossEvent.BossBarColor): BossBarTimer {
    return createTimerTicks(subject, Ticks.seconds(durationSeconds), color)
}

fun MiniGameInstance.createTimerTicks(subject: Any, durationTicks: Int): BossBarTimer {
    return createTimerTicks(subject, durationTicks, BossEvent.BossBarColor.RED)
}

fun MiniGameInstance.createTimerTicks(subject: Any, durationTicks: Int, color: BossEvent.BossBarColor): BossBarTimer {
    val timer = BossBarTimer.builder(translations, subject)
        .withAlertSound(false)
        .withColor(color)
        .withDurationTicks(durationTicks)
        .build()

    timer.addPlayers(PlayerLookup.all(gameHandle.server))
    timer.start(gameHandle.bossBarProvider, gameHandle.scheduler)

    return timer
}

fun MiniGameInstance.useTaskDisplay(): TranslatedBossBar {
    val gameInfo = gameHandle.gameInfo
    val id = gameInfo.identifier("task")

    val bossBar = gameHandle.translations.translateBossBar(id, gameInfo.taskKey, *gameInfo.taskArguments)
        .with(gameHandle.bossBarProvider)
        .formatted(ChatFormatting.GREEN)

    bossBar.setColor(BossEvent.BossBarColor.GREEN)

    bossBar.addPlayers(PlayerLookup.all(server))

    gameHandle.bossBarHandler.showOnJoin(bossBar)

    return bossBar
}

fun MiniGameInstance.usePlayerDynamicTaskDisplay(vararg args: Any?): DynamicTranslatedPlayerBossBar {
    return usePlayerDynamicDisplay(gameHandle.gameInfo.taskKey, *args)
}

fun MiniGameInstance.usePlayerDynamicDisplay(key: String?, vararg args: Any?): DynamicTranslatedPlayerBossBar {
    val id = ApConstants.identifier("task")

    val translations = gameHandle.translations
    val provider = gameHandle.bossBarProvider

    val bossBar = DynamicTranslatedPlayerBossBar(id, key, args, translations, provider)
        .formatted(ChatFormatting.GREEN)

    bossBar.setColor(BossEvent.BossBarColor.GREEN)
    bossBar.setPercent(1f)

    for (player in gameHandle.participants) {
        bossBar.add(player)
    }

    bossBar.init(gameHandle.hooks)

    return bossBar
}
