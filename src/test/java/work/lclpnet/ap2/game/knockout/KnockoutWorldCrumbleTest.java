package work.lclpnet.ap2.game.knockout;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KnockoutWorldCrumbleTest {

    private static final String[] expectedDistances = new String[] {
            """
1 1 1\s
1 0 1\s
1 1 1\s
""",
            """
3 2 2 2 3\s
2 1 1 1 2\s
2 1 0 1 2\s
2 1 1 1 2\s
3 2 2 2 3\s
""",
            """
4 4 3 3 3 4 4\s
4 3 2 2 2 3 4\s
3 2 1 1 1 2 3\s
3 2 1 0 1 2 3\s
3 2 1 1 1 2 3\s
4 3 2 2 2 3 4\s
4 4 3 3 3 4 4\s
""",
            """
6 5 4 4 4 4 4 5 6\s
5 4 4 3 3 3 4 4 5\s
4 4 3 2 2 2 3 4 4\s
4 3 2 1 1 1 2 3 4\s
4 3 2 1 0 1 2 3 4\s
4 3 2 1 1 1 2 3 4\s
4 4 3 2 2 2 3 4 4\s
5 4 4 3 3 3 4 4 5\s
6 5 4 4 4 4 4 5 6\s
"""
    };

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void buildDistancesArray(int radius) {
        var distances = KnockoutWorldCrumble.buildDistancesArray(radius);

        final int len = 2 * radius + 1;

        StringBuilder builder = new StringBuilder();

        for (int z = 0; z < len; z++) {
            for (int x = 0; x < len; x++) {
                builder.append(distances[x][z]).append(' ');
            }

            builder.append('\n');
        }

        assertEquals(expectedDistances[radius - 1], builder.toString());
    }
}