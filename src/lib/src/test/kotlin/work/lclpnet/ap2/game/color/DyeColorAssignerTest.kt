package work.lclpnet.ap2.game.color

import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.world.item.DyeColor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.*

class DyeColorAssignerTest {

    private fun assign(prefs: Map<UUID, List<DyeColor>>, seed: Long = 1L): Map<UUID, DyeColor> =
        DyeColorAssigner.assign(prefs.keys.toList(), { prefs[it] ?: emptyList() }, Random(seed))

    @Test
    fun honorsFirstPriority() {
        val p = UUID(0, 1)
        val result = assign(mapOf(p to listOf(DyeColor.RED)))
        assertEquals(DyeColor.RED, result[p])
    }

    @Test
    fun allDistinctWhenNoConflict() {
        val a = UUID(0, 1)
        val b = UUID(0, 2)
        val c = UUID(0, 3)

        val result = assign(
            mapOf(
                a to listOf(DyeColor.RED),
                b to listOf(DyeColor.BLUE),
                c to listOf(DyeColor.LIME),
            )
        )

        assertEquals(DyeColor.RED, result[a])
        assertEquals(DyeColor.BLUE, result[b])
        assertEquals(DyeColor.LIME, result[c])
    }

    @Test
    fun conflictFallsBackToLowerPriority() {
        val a = UUID(0, 1)
        val b = UUID(0, 2)

        val prefs = mapOf(
            a to listOf(DyeColor.RED, DyeColor.BLUE),
            b to listOf(DyeColor.RED, DyeColor.LIME),
        )

        val result = assign(prefs)

        assertNotEquals(result[a], result[b])
        assertTrue(result[a] == DyeColor.RED || result[b] == DyeColor.RED)
        assertTrue(result[a] in prefs.getValue(a))
        assertTrue(result[b] in prefs.getValue(b))
    }

    @Test
    fun emptyPreferencesStillGetsDistinctColor() {
        val a = UUID(0, 1)
        val b = UUID(0, 2)

        val result = assign(
            mapOf(
                a to listOf(DyeColor.RED),
                b to emptyList(),
            )
        )

        assertEquals(DyeColor.RED, result[a])
        assertNotNull(result[b])
        assertNotEquals(DyeColor.RED, result[b])
    }

    @Test
    fun assignsDistinctColorsToEveryone() {
        val players = (1..10).map { UUID(0, it.toLong()) }
        // everyone wants the same first color -> only one can have it, the rest get distinct leftovers
        val prefs = players.associateWith { listOf(DyeColor.RED) }

        val result = assign(prefs)

        assertEquals(players.size, result.size)
        assertEquals(players.size, result.values.toSet().size)
        assertTrue(result.values.contains(DyeColor.RED))
    }

    @Test
    fun onlyAssignsAvailableColors() {
        val players = (1..4).map { UUID(0, it.toLong()) }
        val prefs = players.associateWith { listOf(DyeColor.RED, DyeColor.BLUE) }
        val available = setOf(DyeColor.LIME, DyeColor.YELLOW, DyeColor.PINK, DyeColor.CYAN)

        val result = DyeColorAssigner.assign(players, { prefs.getValue(it) }, Random(1), available)

        assertTrue(result.values.all { it in available })
        assertEquals(players.size, result.values.toSet().size)
    }

    @Test
    fun unavailablePreferencePullsLowerPriorityUp() {
        val p = UUID(0, 1)
        // 1st choice red is unavailable -> the available 2nd choice blue is used instead
        val result = DyeColorAssigner.assign(
            listOf(p),
            { listOf(DyeColor.RED, DyeColor.BLUE) },
            Random(1),
            setOf(DyeColor.BLUE, DyeColor.LIME),
        )

        assertEquals(DyeColor.BLUE, result[p])
    }

    @Test
    fun allowsDuplicatesWhenFewerColorsThanPlayers() {
        val players = (1..5).map { UUID(0, it.toLong()) }
        val available = setOf(DyeColor.RED, DyeColor.BLUE)

        val result = DyeColorAssigner.assign(players, { emptyList() }, Random(1), available)

        assertEquals(players.size, result.size)
        assertTrue(result.values.all { it in available })
    }

    @Test
    fun repeatsMatchingToMinimizePlayersPerColor() {
        val players = (1..7).map { UUID(0, it.toLong()) }
        val available = setOf(DyeColor.RED, DyeColor.BLUE, DyeColor.LIME)

        val result = DyeColorAssigner.assign(players, { emptyList() }, Random(3), available)

        assertEquals(players.size, result.size)
        assertTrue(result.values.all { it in available })

        // 7 players over 3 colors -> balanced counts of 3, 2, 2
        val maxCount = result.values.groupingBy { it }.eachCount().values.max()
        assertEquals(3, maxCount)
    }

    @Test
    fun leftoverPlayersStillGetPreferredColorInLaterPass() {
        val a = UUID(0, 1)
        val b = UUID(0, 2)
        val c = UUID(0, 3)
        val d = UUID(0, 4)

        val prefs = mapOf(
            a to listOf(DyeColor.RED),
            b to listOf(DyeColor.RED),
            c to listOf(DyeColor.LIME),
            d to listOf(DyeColor.BLUE),
        )
        val available = setOf(DyeColor.RED, DyeColor.LIME, DyeColor.BLUE)

        val result = DyeColorAssigner.assign(prefs.keys.toList(), { prefs.getValue(it) }, Random(1), available)

        // both red-wanters get red (one per pass), the others get their unique preference
        assertEquals(DyeColor.RED, result[a])
        assertEquals(DyeColor.RED, result[b])
        assertEquals(DyeColor.LIME, result[c])
        assertEquals(DyeColor.BLUE, result[d])
    }

    @Test
    fun deterministicWithSeed() {
        val players = (1..8).map { UUID(0, it.toLong()) }
        val prefs = players.associateWith { listOf(DyeColor.RED, DyeColor.BLUE, DyeColor.LIME) }

        val first = DyeColorAssigner.assign(players, { prefs.getValue(it) }, Random(42))
        val second = DyeColorAssigner.assign(players, { prefs.getValue(it) }, Random(42))

        assertEquals(first, second)
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }
}
