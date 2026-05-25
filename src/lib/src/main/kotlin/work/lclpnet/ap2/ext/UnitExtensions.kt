package work.lclpnet.ap2.ext

import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

val Int.ticks: Duration get() = (this * 50L).milliseconds
val Long.ticks: Duration get() = (this * 50L).milliseconds

val Duration.inTicks: Long get() = this.inWholeMilliseconds / 50L
val Duration.inWholeTicks: Long get() = inTicks

fun TimeUnit.toTicks(duration: Long) =
    this.toSeconds(duration) * 20


fun ClosedRange<Duration>.random(): Duration =
    random(Random)

fun ClosedRange<Duration>.random(random: Random): Duration =
    random.nextLong(start.inWholeMilliseconds, endInclusive.inWholeMilliseconds + 1).milliseconds