package work.lclpnet.ap2.game.red_light_green_light

import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.api.stats.FFAStatsManager
import work.lclpnet.ap2.api.stats.Stat
import work.lclpnet.ap2.api.stats.StatUnits
import java.util.*

val Resets = Stat("resets", 0, higherIsBetter = false)
val YellowMovingTime = Stat("yellow_moving_time", 0f, unit = StatUnits.Seconds)
val ClosestStopTime = Stat("closest_stop_time", 0f, higherIsBetter = false, unit = StatUnits.Seconds)
val DistanceReset = Stat("distance_reset", 0f, higherIsBetter = false)
val AvgYellowTimeUsage = Stat("avg_yellow_time_usage", 0f, unit = StatUnits.Percent)

class RedLightGreenLightStats(private val stats: FFAStatsManager) {

    private val yellowTicksThisPhase = HashMap<UUID, Int>()
    private val yellowUsageSum = HashMap<UUID, Double>()
    private val yellowUsageCount = HashMap<UUID, Int>()

    fun reset(player: ServerPlayer) {
        stats.increment(player, Resets)
    }

    fun recordResetDistance(player: ServerPlayer, distance: Double) {
        stats.modify(player, DistanceReset) { it + distance.toFloat() }
    }

    fun movedOnYellow(player: ServerPlayer) {
        stats.modify(player, YellowMovingTime) { it + 1f / 20f }
        yellowTicksThisPhase.merge(player.uuid, 1, Int::plus)
    }

    fun endYellowPhase(player: ServerPlayer, phaseTicks: Int) {
        if (phaseTicks <= 0) return

        val movedTicks = yellowTicksThisPhase.remove(player.uuid) ?: 0
        val ratio = movedTicks.toDouble() / phaseTicks

        val uuid = player.uuid
        val sum = yellowUsageSum.getOrDefault(uuid, 0.0) + ratio
        val count = yellowUsageCount.getOrDefault(uuid, 0) + 1
        yellowUsageSum[uuid] = sum
        yellowUsageCount[uuid] = count

        stats.set(player, AvgYellowTimeUsage, (sum / count).toFloat())
    }

    fun recordStopTime(player: ServerPlayer, seconds: Float) {
        if (seconds < stats.get(player, ClosestStopTime)) {
            stats.set(player, ClosestStopTime, seconds)
        }
    }
}
