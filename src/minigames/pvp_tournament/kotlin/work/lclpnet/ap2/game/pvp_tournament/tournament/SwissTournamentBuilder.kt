package work.lclpnet.ap2.game.pvp_tournament.tournament

import work.lclpnet.ap2.impl.game.data.type.PlayerRef

private data class Player(
    val idx: Int,
    var played: Int = 0,
    var byes: Int = 0,
)

private data class Matchup(
    val round: Int,
    val p1: Int,
    val p2: Int,
)

class SwissTournamentBuilder(
    val matchesPerPlayer: Int,
) : TournamentBuilder {

    override fun build(players: List<PlayerRef>): Tournament {
        if (players.isEmpty()) {
            return Tournament(setOf(), setOf())
        }

        if (players.size == 1) {
            val player = players.first()

            return Tournament(
                (0..<matchesPerPlayer).map { Match(round = it, leftPlayer = player) }.toSet(),
                players.toSet(),
            )
        }

        val modelPlayers = (0..<players.size).map { Player(it) }

        val matchups = buildMatchups(modelPlayers)

        val matches = matchups.map { (round, leftIdx, rightIdx) ->
            Match(
                round = round,
                leftPlayer = players[leftIdx],
                rightPlayer = players[rightIdx]
            )
        }.toSet()

        return Tournament(matches, players.toSet())
    }

    private fun buildMatchups(players: List<Player>): MutableList<Matchup> {
        // For an even number of players, any target match count works
        // For an odd number of players, the target match count needs to be even as one player must receive a
        // "bye" each round. In the final round, all players who still have open matches due to byes will play their
        // final round.
        // In case of an odd number of players, a final round with all players with fewer matches will take place.

        val allMatchups = mutableListOf<Matchup>()

        // every player should be part of exactly `targetMatchCount` matchups
        // if possible, avoid matchups with the same players as opponents

        val playedAgainst = mutableMapOf<Int, MutableSet<Int>>()

        players.forEach { playedAgainst[it.idx] = mutableSetOf() }

        fun uniqueOpponentCount(p: Player): Int =
            players.size - 1 - playedAgainst[p.idx]!!.size

        var round = 0

        while (players.none { it.played == matchesPerPlayer }) {
            // in the final extra round, ignore players that already have enough matches
            val roundPlayers = players.filter { it.played < matchesPerPlayer }.toMutableList()

            if (roundPlayers.size % 2 == 1) {
                val minByeCount = players.minOf { pl -> pl.byes }

                val byePlayer = roundPlayers
                    .filter { it.byes == minByeCount }
                    .minBy { it.played }

                byePlayer.byes++
                roundPlayers.remove(byePlayer)
            }

            val roundPairs = mutableListOf<Matchup>()

            roundPlayers.sortWith(compareBy({ uniqueOpponentCount(it) }, { it.played }))

            while (roundPlayers.size >= 2) {
                // fixate one player at a time
                val p1 = roundPlayers.removeAt(0)

                // prefer opponents who the player hasn't yet played against
                // also choose opponents with the fewest unique opponent count
                // only if there are no unique matchups available anymore, start choosing duplicates
                val candidates = roundPlayers.sortedWith(
                    compareBy(
                        { if (playedAgainst[p1.idx]!!.contains(it.idx)) 1 else 0 },
                        { uniqueOpponentCount(it) }
                    )
                )

                val p2 = candidates.first()
                roundPlayers.remove(p2)

                roundPairs += Matchup(round, p1.idx, p2.idx)
                playedAgainst[p1.idx]!!.add(p2.idx)
                playedAgainst[p2.idx]!!.add(p1.idx)
                p1.played++
                p2.played++
            }

            allMatchups += roundPairs
            round++
        }

        val minPlayedCount = players.minOf { it.played }

        if (minPlayedCount >= matchesPerPlayer) {
            return allMatchups
        }

        // the final round consist out of players who got one more byes than the others
        val finalRound = players.filter { it.played == minPlayedCount }

        if (finalRound.size % 2 == 0) {
            finalRound.chunked(2).forEach { (a, b) ->
                allMatchups += Matchup(round, a.idx, b.idx)
                a.played++
                b.played++
            }
        }

        return allMatchups
    }
}