package work.lclpnet.ap2.base.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameIconMakerTest {

    @Test
    void wrapText() {
        String text = "Find a panda from a picture. First player with %s points wins. After %s rounds, the player with the most points wins!";

        var lines = GameIconMaker.wrapText(text, 24);

        assertEquals(List.of(
                "Find a panda from a",
                "picture. First player",
                "with %s points wins.",
                "After %s rounds, the",
                "player with the most",
                "points wins!"
        ), lines);
    }
}