package work.lclpnet.ap2.game.mining_battle

import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.api.stats.CommonStats
import work.lclpnet.ap2.api.stats.FFAStatsManager
import work.lclpnet.ap2.api.stats.Stat

val OresByValue: Map<Int, Stat<Int>> = (1..5).associateWith { Stat("ores_value_$it", 0) }

val PlayersWeakened = Stat("players_weakened", 0)
val TimesWeakened = Stat("times_weakened", 0, higherIsBetter = false)
val TntDetonated = Stat("tnt_detonated", 0)
val EfficiencyGained = Stat("efficiency_gained", 0)

val allMiningBattleStats: List<Stat<out Any>> = buildList {
    add(CommonStats.BlocksBroken)
    addAll(OresByValue.values)
    add(PlayersWeakened)
    add(TimesWeakened)
    add(TntDetonated)
    add(EfficiencyGained)
}

class MiningBattleStats(private val stats: FFAStatsManager) {

    fun blockBroken(player: ServerPlayer) {
        stats.increment(player, CommonStats.BlocksBroken)
    }

    fun oreBroken(player: ServerPlayer, value: Int) {
        val stat = OresByValue[value] ?: return
        stats.increment(player, stat)
    }

    fun weakenedOthers(player: ServerPlayer) {
        stats.increment(player, PlayersWeakened)
    }

    fun gotWeakened(player: ServerPlayer) {
        stats.increment(player, TimesWeakened)
    }

    fun tntDetonated(player: ServerPlayer) {
        stats.increment(player, TntDetonated)
    }

    fun efficiencyGained(player: ServerPlayer) {
        stats.increment(player, EfficiencyGained)
    }
}
