package work.lclpnet.ap2.game.pvp_tournament.gen

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import work.lclpnet.ap2.game.data.type.PlayerRef
import java.util.*

class TournamentTest {

    @Test
    fun drawSimplifiedStillIncludesChildren() {
        val playerA = PlayerRef(UUID.randomUUID(), "A")
        val playerB = PlayerRef(UUID.randomUUID(), "B")
        val playerC = PlayerRef(UUID.randomUUID(), "C")

        val finale = Match(round = 2, rightPlayer = playerC)
        val leftChild = Match(round = 1, leftPlayer = playerA, rightPlayer = playerB, winnerNext = finale)

        finale.leftChild = leftChild

        val tournament = Tournament(setOf(leftChild, finale), setOf(playerA, playerB, playerC))

        leftChild.complete(null)

        val simplified = tournament.simplifyInPlace()

        assertEquals(tournament.matches, simplified.matches)
        assertEquals(tournament.players, simplified.players)
    }

    @Test
    fun deepCopy() {
        val playerA = PlayerRef(UUID.randomUUID(), "A")
        val playerB = PlayerRef(UUID.randomUUID(), "B")
        val playerC = PlayerRef(UUID.randomUUID(), "C")

        val finale = Match(round = 2, rightPlayer = playerC)
        val leftChild = Match(round = 1, leftPlayer = playerA, rightPlayer = playerB, winnerNext = finale)

        finale.leftChild = leftChild

        val tournament = Tournament(setOf(leftChild, finale), setOf(playerA, playerB, playerC))
        val copy = tournament.deepCopy()

        assertNotSame(tournament, copy)
        assertEquals(tournament.players, copy.players)
        assertEquals(tournament.matches.size, copy.matches.size)
        assertNotSame(tournament.finale, copy.finale)
        assertNotNull(copy.finale.leftChild)
        assertNotSame(tournament.finale.leftChild, copy.finale.leftChild)
        assertNull(copy.finale.rightChild)
        assertNull(copy.finale.leftPlayer)
        assertSame(tournament.finale.rightPlayer, copy.finale.rightPlayer)
        assertFalse(copy.finale.completed)
        assertNull(copy.finale.winner)
        assertSame(tournament.finale.leftChild!!.leftPlayer, copy.finale.leftChild!!.leftPlayer)
        assertSame(tournament.finale.leftChild!!.rightPlayer, copy.finale.leftChild!!.rightPlayer)
        assertFalse(copy.finale.leftChild!!.completed)
        assertNull(copy.finale.leftChild!!.winner)
        assertNotNull(copy.finale.leftChild!!.winnerNext)
        assertNull(copy.finale.leftChild!!.loserNext)
        assertNotSame(tournament.finale.leftChild!!.winnerNext, copy.finale.leftChild!!.winnerNext)
        assertSame(copy.finale, copy.finale.leftChild!!.winnerNext)
    }
}