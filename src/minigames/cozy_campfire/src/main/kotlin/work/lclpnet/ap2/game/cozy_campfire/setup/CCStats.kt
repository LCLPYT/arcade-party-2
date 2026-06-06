package work.lclpnet.ap2.game.cozy_campfire.setup

import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.api.game.team.Team
import work.lclpnet.ap2.api.game.team.TeamManager
import work.lclpnet.ap2.api.stats.CommonStats.DamageDealt
import work.lclpnet.ap2.api.stats.CommonStats.Deaths
import work.lclpnet.ap2.api.stats.CommonStats.KillDeathRatio
import work.lclpnet.ap2.api.stats.CommonStats.Kills
import work.lclpnet.ap2.api.stats.Stat
import work.lclpnet.ap2.api.stats.StatUnits
import work.lclpnet.ap2.api.stats.TeamStatsManager

val FuelAdded = Stat("fuel_added", 0f, unit = StatUnits.Seconds)

class CCStats(private val stats: TeamStatsManager, private val teamManager: TeamManager) {

    fun addFuel(player: ServerPlayer, team: Team, seconds: Float) {
        stats.players.modify(player, FuelAdded) { it + seconds }
        stats.teams.modify(team, FuelAdded) { it + seconds }
    }

    fun addDamage(attacker: ServerPlayer, amount: Float) {
        stats.players.modify(attacker, DamageDealt) { it + amount }

        teamManager.getTeam(attacker).ifPresent { team ->
            stats.teams.modify(team, DamageDealt) { it + amount }
        }
    }

    fun onKill(victim: ServerPlayer, killer: ServerPlayer) {
        val victimTeam = teamManager.getTeam(victim).orElse(null) ?: return
        val killerTeam = teamManager.getTeam(killer).orElse(null) ?: return

        stats.players.increment(killer, Kills)
        stats.teams.increment(killerTeam, Kills)

        stats.players.increment(victim, Deaths)
        stats.teams.increment(victimTeam, Deaths)

        updateKillDeathRatio(killer)
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
