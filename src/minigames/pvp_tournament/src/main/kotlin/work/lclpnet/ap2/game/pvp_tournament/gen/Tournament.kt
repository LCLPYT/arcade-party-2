package work.lclpnet.ap2.game.pvp_tournament.gen

import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import java.util.*

data class Tournament(
    val matches: Set<Match>,
    val players: Set<PlayerRef>,
) {
    val finale: Match get() = matches.first { it.isFinale() }

    /**
     * Eliminates bye matches whose winner is already known.
     *
     * Mutates the contained [Match] objects in place (completes byes, rewires next/child pointers).
     * Callers that need to preserve the original graph should call [deepCopy] first.
     */
    fun simplifyInPlace(): Tournament {
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

    fun deepCopy(): Tournament {
        // children and next matches are wired below
        val copies = IdentityHashMap<Match, Match>(matches.size)

        for (match in matches) {
            copies[match] = match.shallowCopy()
        }

        for ((match, copy) in copies) {
            copy.leftChild = copies[match.leftChild]
            copy.rightChild = copies[match.rightChild]
            copy.winnerNext = copies[match.winnerNext]
            copy.loserNext = copies[match.loserNext]
        }

        return Tournament(
            matches = copies.values.toSet(),
            players = players.toSet(),
        )
    }
}

interface TournamentBuilder {
    fun build(players: List<PlayerRef>): Tournament
}

