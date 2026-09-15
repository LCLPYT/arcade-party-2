package work.lclpnet.ap2.capture_the_flag

import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.api.stats.CommonStats.DamageDealt
import work.lclpnet.ap2.api.stats.CommonStats.Deaths
import work.lclpnet.ap2.api.stats.CommonStats.KillDeathRatio
import work.lclpnet.ap2.api.stats.CommonStats.Kills
import work.lclpnet.ap2.api.stats.Stat
import work.lclpnet.ap2.api.stats.StatUnits
import work.lclpnet.ap2.api.stats.TeamStatsManager
import work.lclpnet.ap2.ext.gainKill
import work.lclpnet.ap2.game.team.TeamManager
import work.lclpnet.kibu.translate.Translations

val FlagsStolen = Stat("flags_stolen", 0)
val FlagsCaptured = Stat("flags_captured", 0)
val FlagsRescued = Stat("flags_rescued", 0)
val ArrowsShot = Stat("arrows_shot", 0)
val ArrowsHit = Stat("arrows_hit", 0)
val ArrowAccuracy = Stat("arrow_accuracy", 0f, unit = StatUnits.Percent)

class CtfStats(
    private val stats: TeamStatsManager,
    private val teamManager: TeamManager,
    private val translations: Translations,
) {

    val players get() = stats.players

    fun flagStolen(player: ServerPlayer) = count(player, FlagsStolen)

    fun flagCaptured(player: ServerPlayer) = count(player, FlagsCaptured)

    fun flagRescued(player: ServerPlayer) = count(player, FlagsRescued)

    fun arrowShot(player: ServerPlayer) {
        count(player, ArrowsShot)
        updateAccuracy(player)
    }

    fun arrowHit(player: ServerPlayer) {
        count(player, ArrowsHit)
        updateAccuracy(player)
    }

    fun damageDealt(attacker: ServerPlayer, amount: Float) {
        stats.players.modify(attacker, DamageDealt) { it + amount }

        teamManager.getTeam(attacker)?.let { team ->
            stats.teams.modify(team, DamageDealt) { it + amount }
        }
    }

    fun onKill(victim: ServerPlayer, killer: ServerPlayer) {
        gainKill(killer, stats.players, translations)
        teamManager.getTeam(killer)?.let { team -> stats.teams.increment(team, Kills) }

        count(victim, Deaths)

        updateKillDeathRatio(killer)
        updateKillDeathRatio(victim)
    }

    fun onDeath(victim: ServerPlayer) {
        count(victim, Deaths)
        updateKillDeathRatio(victim)
    }

    private fun count(player: ServerPlayer, stat: Stat<Int>) {
        stats.players.increment(player, stat)

        teamManager.getTeam(player)?.let { team ->
            stats.teams.increment(team, stat)
        }
    }

    private fun updateKillDeathRatio(player: ServerPlayer) {
        stats.players.set(
            player,
            KillDeathRatio,
            stats.players.get(player, Kills).toFloat() /
                    stats.players.get(player, Deaths).coerceAtLeast(1)
        )
    }

    private fun updateAccuracy(player: ServerPlayer) {
        stats.players.set(player, ArrowAccuracy, accuracy(
            stats.players.get(player, ArrowsHit),
            stats.players.get(player, ArrowsShot)
        ))

        val team = teamManager.getTeam(player) ?: return

        stats.teams.set(team, ArrowAccuracy, accuracy(
            stats.teams.get(team, ArrowsHit),
            stats.teams.get(team, ArrowsShot)
        ))
    }

    private fun accuracy(hits: Int, shots: Int): Float =
        if (shots <= 0) 0f else (hits.toFloat() / shots).coerceIn(0f, 1f)
}
