package work.lclpnet.ap2.ext

import work.lclpnet.ap2.impl.game.MapGameInstance
import work.lclpnet.kibu.scheduler.api.RunningTask
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun MapGameInstance.timeout(ticks: Int = 0, seconds: Int = 0, action: () -> Unit) =
    gameHandle.scheduler.timeout(ticks + seconds * 20, action)!!

fun MapGameInstance.interval(ticks: Int, action: () -> Unit) =
    gameHandle.scheduler.interval(ticks, action)!!

fun MapGameInstance.interval(periodTicks: Int, delayTicks: Int, action: () -> Unit) =
    gameHandle.scheduler.interval(periodTicks, delayTicks, action)!!

fun MapGameInstance.runAfter(
    delay: Duration,
    action: () -> Unit,
)=
    gameHandle.scheduler.timeout(delay.inWholeTicks, action)!!

fun MapGameInstance.runEvery(
    period: Duration,
    after: Duration = 0.seconds,
    action: RunningTask.() -> Unit
) =
    gameHandle.scheduler.interval(period.inWholeTicks, after.inWholeTicks, action)!!

fun MapGameInstance.runEveryTick(action: RunningTask.() -> Unit) =
    runEvery(1.ticks, action = action)

fun MapGameInstance.deferEvery(
    period: Duration,
    after: Duration = period,
    action: RunningTask.() -> Unit
) =
    runEvery(period = period, after = after, action = action)