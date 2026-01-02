package work.lclpnet.ap2.ext

import java.util.concurrent.TimeUnit

fun TimeUnit.toTicks(duration: Long) = this.toSeconds(duration) * 20