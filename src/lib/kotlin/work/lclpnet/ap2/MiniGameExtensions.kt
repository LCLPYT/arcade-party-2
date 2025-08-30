package work.lclpnet.ap2

import work.lclpnet.ap2.impl.game.BaseGameInstance
import work.lclpnet.kibu.scheduler.api.RunningTask

fun BaseGameInstance.players() = gameHandle.participants!!

private fun ticks(ticks: Int, seconds: Int): Int = ticks + seconds * 20

fun BaseGameInstance.timeout(ticks: Int = 0, seconds: Int = 0, action: () -> Unit) =
    gameHandle.gameScheduler.timeout(ticks(ticks, seconds), action)!!

fun BaseGameInstance.timeout(ticks: Int = 0, seconds: Int = 0, action: (RunningTask) -> Unit) =
    gameHandle.gameScheduler.timeout(ticks(ticks, seconds), action)!!

fun BaseGameInstance.interval(ticks: Int, action: () -> Unit) =
    gameHandle.gameScheduler.interval(ticks, action)!!

fun BaseGameInstance.interval(ticks: Int, action: (RunningTask) -> Unit) =
    gameHandle.gameScheduler.interval(ticks, action)!!

fun BaseGameInstance.translate(key: String, vararg args: Any) =
    gameHandle.translations.translateText(key, *args)!!
