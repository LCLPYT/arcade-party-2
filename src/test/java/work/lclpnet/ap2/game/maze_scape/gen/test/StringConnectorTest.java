package work.lclpnet.ap2.game.maze_scape.gen.test;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static work.lclpnet.ap2.game.maze_scape.gen.test.StringPiece.ARROWS;

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
}
