package work.lclpnet.ap2.ext

import work.lclpnet.ap2.impl.game.BaseGameInstance
import work.lclpnet.kibu.scheduler.api.RunningTask
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun BaseGameInstance.timeout(ticks: Int = 0, seconds: Int = 0, action: () -> Unit) =
    gameHandle.scheduler.timeout(ticks + seconds * 20, action)!!

fun BaseGameInstance.interval(ticks: Int, action: () -> Unit) =
    gameHandle.scheduler.interval(ticks, action)!!

fun BaseGameInstance.interval(periodTicks: Int, delayTicks: Int, action: () -> Unit) =
    gameHandle.scheduler.interval(periodTicks, delayTicks, action)!!

fun BaseGameInstance.runAfter(
    delay: Duration,
    action: () -> Unit,
)=
    gameHandle.scheduler.timeout(delay.inWholeTicks, action)!!

fun BaseGameInstance.runAfter(
    delay: TickDuration,
    action: () -> Unit,
)=
    gameHandle.scheduler.timeout(delay.ticks, action)!!

fun BaseGameInstance.runEvery(
    period: Duration,
    after: Duration = 0.seconds,
    action: RunningTask.() -> Unit
) =
    gameHandle.scheduler.interval(period.inWholeTicks, after.inWholeTicks, action)!!

fun BaseGameInstance.runEvery(
    period: TickDuration,
    after: Duration,
    action: RunningTask.() -> Unit
) =
    gameHandle.scheduler.interval(period.ticks, after.inWholeTicks, action)!!

fun BaseGameInstance.runEvery(
    period: Duration,
    after: TickDuration,
    action: RunningTask.() -> Unit
) =
    gameHandle.scheduler.interval(period.inWholeTicks, after.ticks, action)!!

fun BaseGameInstance.runEvery(
    period: TickDuration,
    after: TickDuration = 0.ticks,
    action: RunningTask.() -> Unit
) =
    gameHandle.scheduler.interval(period.ticks, after.ticks, action)!!

fun BaseGameInstance.runEveryTick(action: RunningTask.() -> Unit) =
    runEvery(1.ticks, action = action)