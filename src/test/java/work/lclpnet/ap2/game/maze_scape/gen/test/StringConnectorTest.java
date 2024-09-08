package work.lclpnet.ap2.game.maze_scape.gen.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static work.lclpnet.ap2.game.maze_scape.gen.test.StringConnector.ARROWS;

public class StringConnectorTest {

    @Test
    void testRotateToFace() {
        for (int i = 0; i < 4; i++) {
            var first = new StringConnector(0, 0, i);
            char expected = ARROWS[(i + 2) % 4];

            for (int j = 0; j < 4; j++) {
                var second = new StringConnector(0, 0, j);
                int rot = first.rotateToFace(second);

                assertEquals(expected, ARROWS[Math.floorMod(j + rot, 4)]);
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"0,1", "1,0", "2,-1", "3,0"})
    void testDirectionX(int rot, int x) {
        assertEquals(x, new StringConnector(0, 0, rot).directionX());
    }

    @ParameterizedTest
    @CsvSource({"0,0", "1,1", "2,0", "3,-1"})
    void testDirectionY(int rot, int y) {
        assertEquals(y, new StringConnector(0, 0, rot).directionY());
    }
}
