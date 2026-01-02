package work.lclpnet.ap2.game.pvp_tournament.tournament

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import java.util.*
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.max

class SingleEliminationTournamentBuilderTest {

    @ParameterizedTest
    @ValueSource(ints = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12])
    fun build_n_players(n: Int) {
        val builder = SingleEliminationTournamentBuilder(ByeTracker())
        val players = mutableListOf<PlayerRef>()
        var char = 'A'

        repeat(n) {
            val player = PlayerRef(UUID.randomUUID(), char++.toString())

            players.add(player)
        }

        val tournament = builder.build(players)

        assertEquals(max(1, n - 1), tournament.matches.size)
        assertEquals(players.toSet(), tournament.players.toSet())
        assertEquals(1, tournament.matches.count { it.isFinale() })
        assertEquals(max(1, n / 2), tournament.matches.count { it.isLeaf() })

        tournament.matches.filter { it.isLeaf() }.forEach {
            assertTrue(it.leftPlayer != null || it.rightPlayer == null) {
                "Initial matches must have at least one player"
            }
        }

        assertAllMatchesConnectedAsChildren(tournament)
        assertAllMatchesConnectedAsParents(tournament)

        val expectedMaxRound = max(1, ceil(log2(n.toFloat())).toInt())

        assertEquals(expectedMaxRound, tournament.matches.maxOf { it.round })
    }

    private fun assertAllMatchesConnectedAsParents(tournament: Tournament) {
        val leafs = tournament.matches.filter { it.isLeaf() }.toMutableSet()
        val finale = tournament.finale

        while (leafs.isNotEmpty()) {
            val match = leafs.first()
            leafs.remove(match)

            if (match.winnerNext == null && match.loserNext == null) {
                assertSame(finale, match)
                continue
            }

            match.winnerNext?.let { leafs.add(it) }
            match.loserNext?.let { leafs.add(it) }
        }
    }

    private fun assertAllMatchesConnectedAsChildren(tournament: Tournament) {
        val finale = tournament.finale
        val seen = mutableSetOf(finale)
        val queue = mutableListOf(finale)

        while (queue.isNotEmpty()) {
            val match = queue.removeFirst()

            for (child in match.getChildren()) {
                if (seen.add(child)) {
                    queue.add(child)
                }
            }
        }

        assertEquals(tournament.matches.toSet(), seen)
    }
}