package work.lclpnet.ap2.game.paintball

import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.api.game.team.TeamManager
import work.lclpnet.ap2.api.stats.CommonStats.DamageDealt
import work.lclpnet.ap2.api.stats.CommonStats.Deaths
import work.lclpnet.ap2.api.stats.CommonStats.KillDeathRatio
import work.lclpnet.ap2.api.stats.CommonStats.Kills
import work.lclpnet.ap2.api.stats.Stat
import work.lclpnet.ap2.api.stats.TeamStatsManager
import work.lclpnet.ap2.ext.gainKill
import work.lclpnet.kibu.translate.Translations

val TotalBlocksPainted = Stat("total_blocks_painted", 0)
val BlocksRepainted = Stat("blocks_repainted", 0)
val SpecialItemsUsed = Stat("special_items_used", 0)

class PaintballStats(
    private val stats: TeamStatsManager,
    private val teamManager: TeamManager,
    private val translations: Translations,
) {

    fun blockPainted(player: ServerPlayer, repainted: Boolean) {
        stats.players.increment(player, TotalBlocksPainted)

        teamManager.getTeam(player).ifPresent { team ->
            stats.teams.increment(team, TotalBlocksPainted)
        }

        if (!repainted) return

        stats.players.increment(player, BlocksRepainted)

        teamManager.getTeam(player).ifPresent { team ->
            stats.teams.increment(team, BlocksRepainted)
        }
    }

    fun damageDealt(attacker: ServerPlayer, amount: Float) {
        stats.players.modify(attacker, DamageDealt) { it + amount }

        teamManager.getTeam(attacker).ifPresent { team ->
            stats.teams.modify(team, DamageDealt) { it + amount }
        }
    }

    fun onKill(victim: ServerPlayer, killer: ServerPlayer) {
        gainKill(killer, stats.players, translations)
        stats.players.increment(victim, Deaths)
        updatePlayerKd(killer)
        updatePlayerKd(victim)

        teamManager.getTeam(killer).ifPresent { team ->
            stats.teams.increment(team, Kills)
        }

        teamManager.getTeam(victim).ifPresent { team ->
            stats.teams.increment(team, Deaths)
        }
    }

    fun specialItemUsed(player: ServerPlayer) {
        stats.players.increment(player, SpecialItemsUsed)

        teamManager.getTeam(player).ifPresent { team ->
            stats.teams.increment(team, SpecialItemsUsed)
        }
    }

    private fun updatePlayerKd(player: ServerPlayer) {
        stats.players.set(
            player,
            KillDeathRatio,
            stats.players.get(player, Kills).toFloat() /
                    stats.players.get(player, Deaths).coerceAtLeast(1)
        )
    }
}
