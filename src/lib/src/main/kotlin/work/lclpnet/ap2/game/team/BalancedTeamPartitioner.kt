package work.lclpnet.ap2.game.team

import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.game.player.PlayerRankView
import java.util.*

/**
 * A [TeamPartitioner] that distributes players across teams so that the average overall rank of each
 * team is roughly equal. Best ranked players (rank 1 is best) are spread across the teams instead of
 * accumulating on a single team. Team sizes are kept uniform, like [UniformTeamPartitioner].
 */
class BalancedTeamPartitioner(
    private val rankView: PlayerRankView,
    private val random: Random
) : TeamPartitioner {

    override fun splitIntoTeams(players: Set<ServerPlayer>, teams: Set<Team>): Map<ServerPlayer, Team> {
        require(teams.isNotEmpty()) { "Teams must not be empty" }

        // best ranked players (lowest rank number) first; shuffle first so equal ranks are randomized
        val ordered = players.shuffled(random).sortedBy { rankView.rank(it) }

        // per-team load: member count and accumulated rank sum (seeded from pre-assigned members)
        val counts = mutableMapOf<Team, Int>()
        val rankSums = mutableMapOf<Team, Int>()

        for (team in teams) {
            counts[team] = team.playerCount
            rankSums[team] = team.players.sumOf { rankView.rank(it) }
        }

        // keep sizes uniform first; among equally sized teams, give the next (stronger) player to the
        // team with the highest rank sum (the currently weakest team) to even the averages out
        val queue = PriorityQueue(
            compareBy<Team> { counts.getValue(it) }
                .thenByDescending { rankSums.getValue(it) }
        )

        queue.addAll(teams)

        val mapping = mutableMapOf<ServerPlayer, Team>()

        for (player in ordered) {
            val team = requireNotNull(queue.poll()) { "Team is null" }

            mapping[player] = team
            counts[team] = counts.getValue(team) + 1
            rankSums[team] = rankSums.getValue(team) + rankView.rank(player)

            queue.offer(team)
        }

        return mapping
    }
}
