package work.lclpnet.ap2.ds

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class UnionFindTest {

    @Test
    fun addAndFind() {
        val c = UnionFind<Int>()
        c.add(1)
        assertEquals(1, c.find(1))
        Assertions.assertNull(c.find(2))
    }

    @Test
    fun addAlreadyExisting() {
        val c = UnionFind<Int>()

        (1..3).forEach { c.add(it) }
        (2..3).forEach { c.union(1, it) }
        (1..3).forEach { c.add(it) }

        assertNotNull(c.find(1))
        assertEquals(c.find(1), c.find(2))
        assertEquals(c.find(2), c.find(3))
        assertEquals(setOf(c.find(1)), c.classes())
    }

    @Test
    fun findAllSingle() {
        val c = UnionFind<String>()
        c.add("a")
        assertEquals(setOf("a"), c.findAll("a"))
    }

    @Test
    fun findAllUnknown() {
        val c = UnionFind<Int>()
        Assertions.assertTrue(c.findAll(1).isEmpty())
    }

    @Test
    fun unionTwoElements() {
        val c = UnionFind<Int>()
        c.add(1)
        c.add(2)
        c.union(1, 2)

        assertEquals(c.find(2), c.find(1))
        assertEquals(setOf(1, 2), c.findAll(1))
        assertEquals(setOf(1, 2), c.findAll(2))
    }

    @Test
    fun unionChain() {
        val c = UnionFind<Int>()
        c.add(1)
        c.add(2)
        c.add(3)

        c.union(1, 2)
        c.union(3, 2)

        assertEquals(c.find(2), c.find(1))
        assertEquals(c.find(2), c.find(3))
        assertEquals(setOf(1, 2, 3), c.findAll(3))
    }

    @Test
    fun classesNoneInitially() {
        val c = UnionFind<Int>()

        assertEquals(emptySet<Int>(), c.classes())
    }

    @Test
    fun classesOfSingletons() {
        val c = UnionFind<Int>()

        c.add(1)
        c.add(2)
        c.add(3)

        assertEquals(setOf(1, 2, 3), c.classes())
    }

    @Test
    fun classesOfSimpleUnions() {
        val c = UnionFind<Int>()

        c.add(1)
        c.add(2)
        c.add(3)

        c.union(1, 2)

        assertEquals(setOf(c.find(1), 3), c.classes())

        c.union(2, 3)

        assertEquals(setOf(c.find(1)), c.classes())
    }

    @Test
    fun classesOfMoreUnions() {
        val c = UnionFind<Int>()

        (1..10).forEach { c.add(it) }

        (2..4).forEach { c.union(1, it) }
        (6..8).forEach { c.union(5, it) }
        c.union(9, 10)

        assertEquals(setOf(c.find(2), c.find(7), c.find(10)), c.classes())
    }

    @Test
    fun unionMultipleElements() {
        val c = UnionFind<Int>()

        (1..10).forEach { c.add(it) }

        (2..4).forEach { c.union(1, it) }
        (6..8).forEach { c.union(5, it) }
        c.union(9, 10)

        c.union(2, 6)

        assertEquals(c.find(1), c.find(8))
        assertEquals(setOf(c.find(1), c.find(10)), c.classes())
    }

    @Test
    fun unionSameClassNoOp() {
        val c = UnionFind<Int>()
        c.add(1)
        c.add(2)
        c.union(1, 2)
        c.union(2, 1)

        assertEquals(setOf(1, 2), c.findAll(1))
    }

    @Test
    fun unionWithUnknownNoOp() {
        val c = UnionFind<Int>()
        c.add(1)
        c.union(1, 2)

        assertEquals(1, c.find(1))
        assertEquals(setOf(1), c.findAll(1))
    }

    @Test
    fun unionSelfNoOp() {
        val c = UnionFind<Int>()
        c.add(1)
        c.union(1, 1)

        assertEquals(1, c.find(1))
        assertEquals(setOf(1), c.findAll(1))
    }
}