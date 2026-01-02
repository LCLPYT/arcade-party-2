package work.lclpnet.ap2.game.pvp_tournament.tournament

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class SwissTournamentBuilderTest {

    @ParameterizedTest
    @ValueSource(ints = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12])
    fun build_n(n: Int) {
        val players = (0..<n).map {
            PlayerRef(UUID.randomUUID(), ('A' + it).toString())
        }

        val matchesPerPlayer = 4
        val tournament = SwissTournamentBuilder(matchesPerPlayer).build(players)

        assertEquals(players.toSet(), tournament.players)

        players.forEach { player ->
            assertEquals(matchesPerPlayer, tournament.matches.count { it.players.contains(player) })
        }

        val expectedMatchCount = (max(n, 2) * (matchesPerPlayer / 2f)).roundToInt()

        assertEquals(expectedMatchCount, tournament.matches.size)

        tournament.matches.forEach {
            assertNull(it.leftChild)
            assertNull(it.rightChild)
            assertNull(it.winnerNext)
            assertNull(it.loserNext)

            assertEquals(min(2, n), it.players.size)
        }

        val matchesByRound = tournament.matches.groupBy { it.round }
        val maxRound = tournament.matches.maxOf { it.round }

        matchesByRound.forEach { (round, matches) ->
            val expectedRoundMatchCount = when {
                n == 1 -> 1
                n % 2 == 1 && round == maxRound -> {
                    val expectedByeCount = maxRound % n
                    expectedByeCount / 2
                }
                else -> n / 2
            }

            assertEquals(expectedRoundMatchCount, matches.size) {
                "Expected $expectedRoundMatchCount matches in round $round"
            }
        }
    }
}