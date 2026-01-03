package work.lclpnet.ap2.game.pvp_tournament.gen

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ByeTrackerTest {

    @Test
    fun byeCountInTree() {
        val bt = ByeTracker()

        val match = Match(0).also { bt.register(it) }

        assertEquals(0, bt.byeCountInTree(match))
    }

    @Test
    fun addByeCountIncremented() {
        val bt = ByeTracker()

        val a = Match(0).also { bt.register(it) }
        val b = Match(0).also { bt.register(it) }

        bt.addBye(a)

        assertEquals(1, bt.byeCountInTree(a))
        assertEquals(0, bt.byeCountInTree(b))
    }

    @Test
    fun mergeTreeSummed() {
        val bt = ByeTracker()

        val a = Match(0).also { bt.register(it) }
        val b = Match(0).also { bt.register(it) }
        val c = Match(0).also { bt.register(it) }
        val d = Match(0).also { bt.register(it) }

        bt.addBye(a)
        bt.addBye(b)
        bt.addBye(c)

        bt.mergeTrees(a, b)

        assertEquals(2, bt.byeCountInTree(a))
        assertEquals(2, bt.byeCountInTree(b))

        bt.mergeTrees(c, d)

        assertEquals(1, bt.byeCountInTree(c))
        assertEquals(1, bt.byeCountInTree(d))

        bt.mergeTrees(b, d)

        assertEquals(3, bt.byeCountInTree(a))
    }
}