package work.lclpnet.ap2.game.pvp_tournament.gen

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import work.lclpnet.ap2.impl.game.data.type.PlayerRef
import java.util.UUID

class MatchTest {

    @Test
    fun byeMatchThroughDraw() {
        val playerA = PlayerRef(UUID.randomUUID(), "A")
        val playerB = PlayerRef(UUID.randomUUID(), "B")
        val playerC = PlayerRef(UUID.randomUUID(), "C")

        val finale = Match(round = 2, leftPlayer = playerA)
        val child = Match(round = 1, winnerNext = finale, leftPlayer = playerB, rightPlayer = playerC)

        finale.rightChild = child

        child.complete(null)

        assertTrue(finale.completed)
        assertSame(playerA, finale.winner)
    }

    @Test
    fun multiDrawLeedsToDrawInParent() {
        val playerA = PlayerRef(UUID.randomUUID(), "A")
        val playerB = PlayerRef(UUID.randomUUID(), "B")
        val playerC = PlayerRef(UUID.randomUUID(), "C")
        val playerD = PlayerRef(UUID.randomUUID(), "D")

        val finale = Match(round = 2)
        val leftChild = Match(round = 1, winnerNext = finale, leftPlayer = playerA, rightPlayer = playerB)
        val rightChild = Match(round = 1, winnerNext = finale, leftPlayer = playerC, rightPlayer = playerD)

        finale.leftChild = leftChild
        finale.rightChild = rightChild

        leftChild.complete(null)

        assertFalse(finale.completed)
        assertNull(finale.winner)
        assertTrue(finale.isBye())  // should already be a bye match now, because only one child is remaining

        rightChild.complete(null)

        assertTrue(finale.isBye())  // should still be a bye match
        assertTrue(finale.completed)
        assertNull(finale.winner)
    }
}