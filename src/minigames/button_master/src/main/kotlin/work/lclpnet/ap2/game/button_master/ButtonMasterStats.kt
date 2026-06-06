package work.lclpnet.ap2.game.button_master

import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.api.stats.FFAStatsManager
import work.lclpnet.ap2.api.stats.Stat

val ButtonsFound = Stat("buttons_found", 0)
val Escapes = Stat("escapes", 0)
val ButtonsMissed = Stat("buttons_missed", 0, higherIsBetter = false)

class ButtonMasterStats(private val stats: FFAStatsManager) {

    fun buttonFound(player: ServerPlayer) {
        stats.increment(player, ButtonsFound)
    }

    fun escaped(player: ServerPlayer) {
        stats.increment(player, Escapes)
    }

    fun buttonMissed(player: ServerPlayer) {
        stats.increment(player, ButtonsMissed)
    }
}
