package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.game.maze_scape.gen.test.StringPiece;
import work.lclpnet.ap2.game.maze_scape.gen.test.String2dVoxelView;
import work.lclpnet.ap2.impl.util.BlockBox;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GreedyMeshingTest {

    @Test
    void generateBoxes() {
        var piece = new StringPiece("""
                          ██  \s
                        ███████
                  ██    ███████
                ███████████████
                  █████████████
                  █████████████
                ███████████████
                        ███████
                             ██""");

        int width = piece.width(), height = piece.height();
        var struct = new String2dVoxelView(piece.getString(), width, height);
        var meshing = new GreedyMeshing(width, height, 1, struct);

        var boxes = meshing.generateBoxes();

        String expected = """
                          aa  \s
                        bbaaccc
                  dd    bbaaccc
                eeddffffbbaaccc
                  ddffffbbaaccc
                  ddffffbbaaccc
                ggddffffbbaaccc
                        bbaaccc
                             hh""";

        String actual = genString(height, width, boxes);

        assertEquals(expected, actual);
    }

    private static @NotNull String genString(int height, int width, List<BlockBox> boxes) {
        char[][] chars = new char[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                chars[y][x] = ' ';
            }
        }

        char c = 'a';

        for (BlockBox box : boxes) {
            for (BlockPos pos : box) {
                chars[pos.getY()][pos.getX()] = c;
            }
            c++;
        }

        return Arrays.stream(chars)
                .map(String::new)
                .collect(Collectors.joining("\n"));
    }
}