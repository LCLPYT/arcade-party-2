package work.lclpnet.ap2.game.maze_scape.gen.test;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StringPieceTest {

    @Test
    void stringPieceIsPadded() {
        var start = new StringPiece("""
                ───
                ←
                ┐ ┌
                """);

        assertEquals("""
                ───
                ← \s
                ┐ ┌""", start.getString());
    }

    @Test
    void stringPieceDimensions() {
        var start = new StringPiece("""
                ────
                
                ┐ ┌─""");

        assertEquals(4, start.width());
        assertEquals(3, start.height());
    }

    @Test
    void stringPieceConnectors() {
        var start = new StringPiece("""
                ┘↑└
                ← →
                ┐↓┌""");

        assertEquals(List.of(
                new StringConnector(1, 0, 3),
                new StringConnector(0, 1, 2),
                new StringConnector(2, 1, 0),
                new StringConnector(1, 2, 1)
        ), start.connectors());
    }
}
