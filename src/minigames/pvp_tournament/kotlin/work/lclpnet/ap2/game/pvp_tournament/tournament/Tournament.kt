package work.lclpnet.ap2.game.pvp_tournament.tournament

import work.lclpnet.ap2.impl.game.data.type.PlayerRef

class Tournament(
    val matches: List<Match>,
    val players: List<PlayerRef>,
)

interface TournamentBuilder {
    fun build(players: List<PlayerRef>): Tournament
}

class SingleEliminationTournamentBuilder(
    val byeTracker: ByeTracker,
): TournamentBuilder {

    override fun build(players: List<PlayerRef>): Tournament {
        val players = players.shuffled().toMutableList()
        val matches = mutableListOf<Match>()

        // start with a bye match for each player
        val initial = players.map { player -> Match(leftPlayer = player) }

        initial.forEach { byeTracker.register(it) }

        var current = initial

        do {
            val round = buildRound(current)

            matches.addAll(round)

            current = round
        } while (current.size > 1)

        return Tournament(matches, players)
    }

    fun buildRound(matches: List<Match>): List<Match> {
        val pool = matches.toMutableList()
        val round = mutableListOf<Match>()

        if (pool.size % 2 == 1) {
            val match = requireNotNull(byeTracker.chooseBye(pool)) { "Bye match could not be chosen" }
            pool.remove(match)

            val byeMatch = Match(leftChild = match)
            byeTracker.register(byeMatch)
            byeTracker.mergeTrees(match, byeMatch)
            byeTracker.addBye(byeMatch)

            round.add(byeMatch)
        }

        pool.chunked(2).map {
            Match(
                leftChild = it[0],
                rightChild = it[1],
            )
        }.forEach {
            round.add(it)
            byeTracker.register(it)
        }

        return round
    }
}