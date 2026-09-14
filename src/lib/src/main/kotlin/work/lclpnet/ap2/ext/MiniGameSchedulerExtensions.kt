package work.lclpnet.ap2.ext

import work.lclpnet.ap2.api.SchedulerHolder
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.kibu.scheduler.api.RunningTask
import work.lclpnet.kibu.scheduler.api.TaskHandle
import work.lclpnet.kibu.scheduler.api.TaskScheduler
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun MiniGameInstance.timeout(ticks: Int = 0, seconds: Int = 0, action: () -> Unit) =
    gameHandle.scheduler.timeout(ticks + seconds * 20, action)!!

fun MiniGameInstance.interval(ticks: Int, action: () -> Unit) =
    gameHandle.scheduler.interval(ticks, action)!!

fun MiniGameInstance.interval(periodTicks: Int, delayTicks: Int, action: () -> Unit) =
    gameHandle.scheduler.interval(periodTicks, delayTicks, action)!!


context(scheduler: TaskScheduler)
fun runAfter(
    delay: Duration,
    action: () -> Unit,
) =
    scheduler.timeout(delay.inWholeTicks, action)!!

// TODO reuse runAfter by explicitly passing scheduler once kotlin promotes it to stable
context(holder: SchedulerHolder)
fun runAfter(
    delay: Duration,
    action: () -> Unit,
) =
    holder.scheduler.timeout(delay.inWholeTicks, action)!!


context(scheduler: TaskScheduler)
fun runEvery(
    period: Duration,
    after: Duration = 0.seconds,
    action: RunningTask.() -> Unit
): TaskHandle = scheduler.interval(
    period.inWholeTicks,
    after.inWholeTicks,
    action,
)

// TODO reuse runEvery by explicitly passing scheduler once kotlin promotes it to stable
context(holder: SchedulerHolder)
fun runEvery(
    period: Duration,
    after: Duration = 0.seconds,
    action: RunningTask.() -> Unit
): TaskHandle = holder.scheduler.interval(
    period.inWholeTicks,
    after.inWholeTicks,
    action,
)


context(scheduler: TaskScheduler)
fun runEveryTick(action: RunningTask.() -> Unit) =
    runEvery(1.ticks, action = action)

// TODO reuse runEveryTick by explicitly passing scheduler once kotlin promotes it to stable
context(holder: SchedulerHolder)
fun runEveryTick(action: RunningTask.() -> Unit) =
    runEvery(1.ticks, action = action)


context(scheduler: TaskScheduler)
fun deferEvery(
    period: Duration,
    after: Duration = period,
    action: RunningTask.() -> Unit
) =
    runEvery(period = period, after = after, action = action)

// TODO reuse deferEvery by explicitly passing scheduler once kotlin promotes it to stable
context(holder: SchedulerHolder)
fun deferEvery(
    period: Duration,
    after: Duration = period,
    action: RunningTask.() -> Unit
) =
    runEvery(period = period, after = after, action = action)
