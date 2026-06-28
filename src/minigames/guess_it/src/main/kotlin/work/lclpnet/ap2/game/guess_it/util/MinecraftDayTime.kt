package work.lclpnet.ap2.game.guess_it.util

import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.math.roundToInt

object MinecraftDayTime {
    private val HH_MM_SS: Pattern = Pattern.compile("([-+]?\\d+)(?::([-+]?\\d+))?(?::([-+]?\\d+))?")
    private val HH_MM_SS_12H: Pattern = Pattern.compile("([-+]?\\d+)(?::([-+]?\\d+))?(?::([-+]?\\d+))?\\s*(am|pm)")

    fun stringifyDayTime(time: Int): String {
        var time = time
        time %= 24000

        val hours = (time / 1000 + 6) % 24
        val minutes = ((time % 1000 * 60) / 1000f).roundToInt() % 60

        return "%02d:%02d".format(hours, minutes)
    }

    fun dayTimeValue(str: String): String? {
        val time = parseDayTime(str) ?: return null

        return stringifyDayTime(time)
    }

    fun parseDayTime(str: String): Int? {
        var str = str
        str = str.lowercase()

        var matcher = HH_MM_SS_12H.matcher(str)

        if (matcher.find()) {
            val modifier = matcher.group(4)

            return parseHourMinuteSecond(matcher)?.takeIf { res ->
                res.hour in 0..12 && res.minute in 0..60 && res.second in 0..60
            }?.let { res ->
                var hour = res.hour % 12

                if ("pm" == modifier) {
                    hour += 12
                }

                toDayTime(hour, res.minute, res.second)
            }
        }

        matcher = HH_MM_SS.matcher(str)

        if (matcher.find()) {
            return parseHourMinuteSecond(matcher)?.takeIf{ res: Result ->
                res.hour in 0..24 && res.minute in 0..60 && res.second in 0..60
            }?.let { res ->
                toDayTime(res.hour, res.minute, res.second)
            }
        }

        return null
    }

    fun toDayTime(hour: Int, minute: Int, second: Int): Int =
        (Math.floorMod(hour - 6, 24) * 1000
                + (Math.floorMod(minute, 60) * 1000 / 60f).roundToInt()
                + (Math.floorMod(second, 60) * 1000 / 3600f).roundToInt())

    private fun parseHourMinuteSecond(matcher: Matcher): Result? {
        val hourStr = matcher.group(1)
        val minuteStr = matcher.group(2)
        val secondStr = matcher.group(3)

        val hour: Int
        var minute = 0
        var second = 0

        try {
            hour = hourStr.toInt(10)

            if (minuteStr != null) {
                minute = minuteStr.toInt(10)
            }

            if (secondStr != null) {
                second = secondStr.toInt(10)
            }
        } catch (_: NumberFormatException) {
            return null
        }

        return Result(hour, minute, second)
    }

    private data class Result(val hour: Int, val minute: Int, val second: Int)
}
