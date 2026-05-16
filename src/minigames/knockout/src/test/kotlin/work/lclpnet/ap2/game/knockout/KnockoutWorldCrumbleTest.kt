package work.lclpnet.ap2.game.knockout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class KnockoutWorldCrumbleTest {

    @ParameterizedTest
    @ValueSource(ints = [1, 2, 3, 4])
    fun `buildDistancesArray builds correct distances`(radius: Int) {
        val distances = buildDistancesArray(radius)

        val len = 2 * radius + 1

        val builder = StringBuilder()

        for (z in 0 until len) {
            for (x in 0 until len) {
                builder.append(distances[x][z]).append(' ')
            }
            builder.append('\n')
        }

        assertEquals(expectedDistances[radius - 1], builder.toString())
    }

    companion object {
        private val expectedDistances = arrayOf(
            "1 1 1 \n1 0 1 \n1 1 1 \n",
            "3 2 2 2 3 \n2 1 1 1 2 \n2 1 0 1 2 \n2 1 1 1 2 \n3 2 2 2 3 \n",
            "4 4 3 3 3 4 4 \n4 3 2 2 2 3 4 \n3 2 1 1 1 2 3 \n3 2 1 0 1 2 3 \n3 2 1 1 1 2 3 \n4 3 2 2 2 3 4 \n4 4 3 3 3 4 4 \n",
            "6 5 4 4 4 4 4 5 6 \n5 4 4 3 3 3 4 4 5 \n4 4 3 2 2 2 3 4 4 \n4 3 2 1 1 1 2 3 4 \n4 3 2 1 0 1 2 3 4 \n4 3 2 1 1 1 2 3 4 \n4 4 3 2 2 2 3 4 4 \n5 4 4 3 3 3 4 4 5 \n6 5 4 4 4 4 4 5 6 \n"
        )
    }
}
