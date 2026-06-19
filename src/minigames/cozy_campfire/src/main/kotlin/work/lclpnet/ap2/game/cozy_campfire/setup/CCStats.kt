package work.lclpnet.ap2.game.cozy_campfire.setup

import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.api.stats.CommonStats.DamageDealt
import work.lclpnet.ap2.api.stats.CommonStats.Deaths
import work.lclpnet.ap2.api.stats.CommonStats.KillDeathRatio
import work.lclpnet.ap2.api.stats.CommonStats.Kills
import work.lclpnet.ap2.api.stats.Stat
import work.lclpnet.ap2.api.stats.StatUnits
import work.lclpnet.ap2.api.stats.TeamStatsManager
import work.lclpnet.ap2.ext.gainKill
import work.lclpnet.ap2.game.team.Team
import work.lclpnet.ap2.game.team.TeamManager
import work.lclpnet.kibu.translate.Translations

val FuelAdded = Stat("fuel_added", 0f, unit = StatUnits.Seconds)
val FuelRemaining = Stat("fuel_remaining", 0f, unit = StatUnits.Seconds)

class CCStats(
    private val stats: TeamStatsManager,
    private val teamManager: TeamManager,
    private val translations: Translations
) {

    fun addFuel(player: ServerPlayer, team: Team, seconds: Float) {
        stats.players.modify(player, FuelAdded) { it + seconds }
        stats.teams.modify(team, FuelAdded) { it + seconds }
    }

    fun setRemainingFuel(team: Team, seconds: Float) {
        stats.teams.set(team, FuelRemaining, seconds)
    }

    fun addDamage(attacker: ServerPlayer, amount: Float) {
        stats.players.modify(attacker, DamageDealt) { it + amount }

        teamManager.getTeam(attacker)?.let { team ->
            stats.teams.modify(team, DamageDealt) { it + amount }
        }
    }

    fun onKillGained(killer: ServerPlayer) {
        val killerTeam = teamManager.getTeam(killer) ?: return

        gainKill(killer, stats.players, translations)
        stats.teams.increment(killerTeam, Kills)

        updateKillDeathRatio(killer)
    }

    fun onDeath(victim: ServerPlayer) {
        val victimTeam = teamManager.getTeam(victim) ?: return

        stats.players.increment(victim, Deaths)
        stats.teams.increment(victimTeam, Deaths)

        updateKillDeathRatio(victim)
    }

    private fun updateKillDeathRatio(player: ServerPlayer) {
        stats.players.set(
            player,
            KillDeathRatio,
            stats.players.get(player, Kills).toFloat() /
                    stats.players.get(player, Deaths).coerceAtLeast(1)
        )
    }
}
