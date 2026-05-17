package work.lclpnet.ap2.ext

import java.util.concurrent.TimeUnit
import kotlin.time.Duration

fun TimeUnit.toTicks(duration: Long) =
    this.toSeconds(duration) * 20

val Duration.inWholeTicks: Long get() =
    inWholeMilliseconds / 50

@JvmInline
value class TickDuration(val ticks: Long) : Comparable<TickDuration> {
    override fun compareTo(other: TickDuration): Int = (this.ticks - other.ticks).toInt()
}

val Int.ticks: TickDuration get() =
    TickDuration(this.toLong())

val Long.ticks: TickDuration get() =
    TickDuration(this)