package work.lclpnet.ap2.game.pvp_tournament.gen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import java.util.*

class TournamentTest {

    @Test
    fun drawSimplifiedStillIncludesChildren() {
        val playerA = PlayerRef(UUID.randomUUID(), "A")
        val playerB = PlayerRef(UUID.randomUUID(), "B")
        val playerC = PlayerRef(UUID.randomUUID(), "C")

        val finale = Match(round = 2, rightPlayer = playerC)
        val leftChild = Match(round = 1, leftPlayer = playerA, rightPlayer = playerB, winnerNext = finale)

        val tournament = Tournament(setOf(leftChild, finale), setOf(playerA, playerB, playerC))

        finale.leftChild = leftChild

        leftChild.complete(null)

        val simplified = tournament.simplified()

        assertEquals(tournament.matches, simplified.matches)
        assertEquals(tournament.players, simplified.players)
    }
}