package work.lclpnet.ap2.game.team

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import net.minecraft.server.level.ServerPlayer
import java.util.*

class UniformTeamPartitioner(private val random: Random) : TeamPartitioner {
    override fun splitIntoTeams(players: Set<ServerPlayer>, teams: Set<Team>): Map<ServerPlayer, Team> {
        require(!teams.isEmpty()) { "Teams must not be empty" }

        val playerCount = Object2IntOpenHashMap<Team>(teams.size)

        for (team in teams) {
            playerCount.put(team, team.playerCount)
        }

        // fill the teams with the fewest members first
        val queue = PriorityQueue(Comparator.comparingInt { key: Team? ->
            playerCount.getInt(key)
        })

        queue.addAll(teams)

        val playerList = players.toMutableList()
        val mapping = mutableMapOf<ServerPlayer, Team>()

        while (playerList.isNotEmpty()) {
            val player = playerList.removeAt(random.nextInt(playerList.size))

            val team = Objects.requireNonNull(queue.poll(), "Team is null")

            mapping[player] = team

            playerCount.compute(team) { _, v: Int? ->
                if (v == null) 1 else v + 1
            }

            queue.offer(team)
        }

        return mapping
    }
}