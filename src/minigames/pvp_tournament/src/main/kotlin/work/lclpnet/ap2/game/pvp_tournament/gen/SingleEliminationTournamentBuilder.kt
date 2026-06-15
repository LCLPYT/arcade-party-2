package work.lclpnet.ap2.game.pvp_tournament.gen

import work.lclpnet.ap2.game.data.type.PlayerRef
import kotlin.math.max

class SingleEliminationTournamentBuilder(
    val byeTracker: ByeTracker,
): TournamentBuilder {

    override fun build(players: List<PlayerRef>): Tournament {
        val players = players.shuffled().toMutableList()
        val matches = mutableSetOf<Match>()

        // start with a bye match for each player
        val initial = players.map { player -> Match(round = 0, leftPlayer = player) }

        matches.addAll(initial)
        initial.forEach { byeTracker.register(it) }

        var current = initial

        do {
            val round = buildRound(current)

            matches.addAll(round)

            current = round
        } while (current.size > 1)

        return Tournament(matches, players.toSet()).simplifyInPlace()
    }

    fun buildRound(matches: List<Match>): List<Match> {
        val pool = matches.toMutableList()
        val round = mutableListOf<Match>()

        if (pool.size % 2 == 1) {
            val match = requireNotNull(byeTracker.chooseBye(pool)) { "Bye match could not be chosen" }
            pool.remove(match)

            val byeMatch = Match(round = match.round + 1, leftChild = match)

            match.winnerNext = byeMatch

            round.add(byeMatch)
            byeTracker.register(byeMatch)

            byeTracker.mergeTrees(match, byeMatch)
            byeTracker.addBye(byeMatch)
        }

        pool.chunked(2).forEach {
            val match = Match(
                round = max(it[0].round, it[1].round) + 1,
                leftChild = it[0],
                rightChild = it[1],
            )

            it[0].winnerNext = match
            it[1].winnerNext = match

            round.add(match)
            byeTracker.register(match)
        }

        return round
    }
}