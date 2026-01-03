package work.lclpnet.ap2.game.pvp_tournament.gen

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

