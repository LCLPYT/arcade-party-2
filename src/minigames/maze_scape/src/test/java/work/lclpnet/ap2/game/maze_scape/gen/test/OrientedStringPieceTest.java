package work.lclpnet.ap2.game.maze_scape.gen.test;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OrientedStringPieceTest {

    @Test
    void orientedStringPieceRotate0() {
        var piece = new StringPiece("""
                ────
                ←  →
                ┐↓┌─""");

        var oriented = new OrientedStringPiece(piece, 0, 0, 0);

        assertEquals(4, oriented.width());
        assertEquals(3, oriented.height());
        assertEquals("""
                ────
                ←  →
                ┐↓┌─""", oriented.string());
    }

    @Test
    void orientedStringPieceRotate90() {
        var piece = new StringPiece("""
                ────
                ←  →
                ┐↓┌─""");

        var oriented = new OrientedStringPiece(piece, 0, 0, 1);

        assertEquals(3, oriented.width());
        assertEquals(4, oriented.height());
        assertEquals("""
                ┘↑│
                ← │
                ┐ │
                │↓│""", oriented.string());
    }

    @Test
    void orientedStringPieceRotate180() {
        var piece = new StringPiece("""
                ────
                ←  →
                ┐↓┌─""");

        var oriented = new OrientedStringPiece(piece, 0, 0, 2);

        assertEquals(4, oriented.width());
        assertEquals(3, oriented.height());
        assertEquals("""
                ─┘↑└
                ←  →
                ────""", oriented.string());
    }

    @Test
    void orientedStringPieceRotate270() {
        var piece = new StringPiece("""
                ────
                ←  →
                ┐↓┌─""");

        var oriented = new OrientedStringPiece(piece, 0, 0, 3);

        assertEquals(3, oriented.width());
        assertEquals(4, oriented.height());
        assertEquals("""
                │↑│
                │ └
                │ →
                │↓┌""", oriented.string());
    }
}
