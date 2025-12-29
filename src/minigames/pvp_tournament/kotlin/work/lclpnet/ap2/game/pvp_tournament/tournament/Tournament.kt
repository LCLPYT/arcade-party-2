package work.lclpnet.ap2.game.pvp_tournament.tournament

import work.lclpnet.ap2.impl.game.data.type.PlayerRef

data class Tournament(
    val matches: Set<Match>,
    val players: Set<PlayerRef>,
) {
    val finale: Match get() = matches.first { it.isFinale() }

    fun simplified(): Tournament {
        val newMatches = matches.toMutableSet()
        val checkOptimize = newMatches.toMutableSet()

        // eliminate bye matches where the winner is already known
        while (checkOptimize.isNotEmpty()) {
            val match = checkOptimize.first()
            checkOptimize.remove(match)

            if (!match.isBye()) continue

            val parentMatch = match.winnerNext ?: continue
            val winner = match.byeMatchWinner()

            match.complete(winner)

            newMatches.remove(match)

            parentMatch.acceptPlayerFromChildMatch(winner, match)

            val childMatch = match.byeMatchChild()

            if (childMatch != null) {
                when {
                    childMatch.winnerNext == match -> {
                        childMatch.winnerNext = parentMatch
                    }
                    childMatch.loserNext == match -> {
                        childMatch.loserNext = parentMatch
                    }
                }
            }

            // remove child from parent, after accepting the player
            when {
                parentMatch.leftChild == match -> {
                    parentMatch.leftChild = childMatch
                }
                parentMatch.rightChild == match -> {
                    parentMatch.rightChild = childMatch
                }
            }

            // propagate optimization
            checkOptimize.add(parentMatch)
        }

        return Tournament(newMatches, players)
    }
}

interface TournamentBuilder {
    fun build(players: List<PlayerRef>): Tournament
}

class SingleEliminationTournamentBuilder(
    val byeTracker: ByeTracker,
): TournamentBuilder {

    override fun build(players: List<PlayerRef>): Tournament {
        val players = players.shuffled().toMutableList()
        val matches = mutableSetOf<Match>()

        // start with a bye match for each player
        val initial = players.map { player -> Match(leftPlayer = player) }

        matches.addAll(initial)
        initial.forEach { byeTracker.register(it) }

        var current = initial

        do {
            val round = buildRound(current)

            matches.addAll(round)

            current = round
        } while (current.size > 1)

        return Tournament(matches, players.toSet()).simplified()
    }

    fun buildRound(matches: List<Match>): List<Match> {
        val pool = matches.toMutableList()
        val round = mutableListOf<Match>()

        if (pool.size % 2 == 1) {
            val match = requireNotNull(byeTracker.chooseBye(pool)) { "Bye match could not be chosen" }
            pool.remove(match)

            val byeMatch = Match(leftChild = match)

            match.winnerNext = byeMatch

            round.add(byeMatch)
            byeTracker.register(byeMatch)

            byeTracker.mergeTrees(match, byeMatch)
            byeTracker.addBye(byeMatch)
        }

        pool.chunked(2).forEach {
            val match = Match(
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