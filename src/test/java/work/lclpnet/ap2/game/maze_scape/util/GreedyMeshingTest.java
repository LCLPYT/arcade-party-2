package work.lclpnet.ap2.game.maze_scape.util;

import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.game.maze_scape.gen.test.String2dVoxelView;
import work.lclpnet.ap2.game.maze_scape.gen.test.String3dVoxelView;
import work.lclpnet.ap2.game.maze_scape.gen.test.StringPiece;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GreedyMeshingTest {

    @Test
    void generateBoxes_2d() {
        var piece = new StringPiece("""
                0000000000██000
                00000000███████
                00██0000███████
                ███████████████
                00█████████████
                00█████████████
                ███████████████
                00000000███████
                0000000000000██""");

        int width = piece.width(), height = piece.height();
        var struct = new String2dVoxelView(piece.getString(), width, height);
        var meshing = new GreedyMeshing(width, height, 1, struct);

        var boxes = meshing.generateBoxes();

        String expected = """
                0000000000aa000
                00000000bbaaccc
                00dd0000bbaaccc
                eeddffffbbaaccc
                00ddffffbbaaccc
                00ddffffbbaaccc
                ggddffffbbaaccc
                00000000bbaaccc
                0000000000000hh""";

        String actual = String2dVoxelView.genString(height, width, boxes);

        assertEquals(expected, actual);
    }

    @Test
    void generateBoxes_3d() {
        var y0 = new StringPiece("""
                000000000000000
                00000000███████
                00000000███████
                00█████████████
                00█████████████
                00█████████████
                00█████████████
                00000000███████
                000000000000000""");
        var y1 = new StringPiece("""
                0000000000██000
                00000000███████
                00██0000███████
                ███████████████
                00█████████████
                00█████████████
                ███████████████
                00000000███████
                0000000000000██""");
        var y2 = new StringPiece("""
                000000000000000
                00000000███████
                00000000███████
                00000000███████
                00000000███████
                00000000███████
                00000000███████
                00000000███████
                000000000000000""");

        int width = y1.width(), height = 3, length = y1.height();

        assertEquals(width, y0.width());
        assertEquals(width, y2.width());
        assertEquals(length, y0.height());
        assertEquals(length, y2.height());

        var struct = new String3dVoxelView(new String[] {
                y0.getString(), y1.getString(), y2.getString()
        }, width, height, length);
        var meshing = new GreedyMeshing(width, height, length, struct);

        var boxes = meshing.generateBoxes();

        String[] expected = new String[] {
                """
                  000000000000000
                  00000000aaaaaaa
                  00000000aaaaaaa
                  00bbbbbbaaaaaaa
                  00bbbbbbaaaaaaa
                  00bbbbbbaaaaaaa
                  00bbbbbbaaaaaaa
                  00000000aaaaaaa
                  000000000000000""",
                """
                  0000000000cc000
                  00000000aaaaaaa
                  00dd0000aaaaaaa
                  eebbbbbbaaaaaaa
                  00bbbbbbaaaaaaa
                  00bbbbbbaaaaaaa
                  ffbbbbbbaaaaaaa
                  00000000aaaaaaa
                  0000000000000gg""",
                """
                  000000000000000
                  00000000aaaaaaa
                  00000000aaaaaaa
                  00000000aaaaaaa
                  00000000aaaaaaa
                  00000000aaaaaaa
                  00000000aaaaaaa
                  00000000aaaaaaa
                  000000000000000"""};

        String[] actual = String3dVoxelView.genStrings(height, width, length, boxes);

        assertArrayEquals(expected, actual);
    }
}