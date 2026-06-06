package work.lclpnet.ap2.game.red_light_green_light

import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.api.stats.FFAStatsManager
import work.lclpnet.ap2.api.stats.Stat

val Resets = Stat("resets", 0, higherIsBetter = false)
val YellowMovingTime = Stat("yellow_moving_time", 0f)
val ClosestStopTime = Stat("closest_stop_time", 0f, higherIsBetter = false)

class RedLightGreenLightStats(private val stats: FFAStatsManager) {

    fun reset(player: ServerPlayer) {
        stats.increment(player, Resets)
    }

    fun movedOnYellow(player: ServerPlayer) {
        stats.modify(player, YellowMovingTime) { it + 1f / 20f }
    }

    fun recordStopTime(player: ServerPlayer, seconds: Float) {
        if (seconds < stats.get(player, ClosestStopTime)) {
            stats.set(player, ClosestStopTime, seconds)
        }
    }
}
