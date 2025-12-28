package work.lclpnet.ap2.game.pvp_tournament.tournament

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import java.util.*

class SingleEliminationTournamentBuilderTest {

    @ParameterizedTest
    @ValueSource(ints = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12])
    fun build_n_players(playerCount: Int) {
        val builder = SingleEliminationTournamentBuilder(ByeTracker())
        val players = mutableListOf<PlayerRef>()

        repeat(playerCount) {
            val player = PlayerRef.createForUuid(UUID.randomUUID())

            players.add(player)
        }

        val tournament = builder.build(players)

        fun matchCount(count: Int): Int = when {
            count >= 3 -> {
                // in each level, the count is halved, but bye-matches are also counted as match
                val inLevel = count / 2 + count % 2

                inLevel + matchCount(inLevel)
            }
            count >= 1 -> 1
            else -> 0
        }

        assertEquals(matchCount(playerCount), tournament.matches.size)
        assertEquals(players.toSet(), tournament.players.toSet())
    }
}