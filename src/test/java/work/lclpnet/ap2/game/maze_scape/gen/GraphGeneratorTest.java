package work.lclpnet.ap2.game.maze_scape.gen;

import org.apache.commons.lang3.mutable.MutableInt;
import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.game.maze_scape.gen.test.OrientedStringPiece;
import work.lclpnet.ap2.game.maze_scape.gen.test.String2DGeneratorDomain;
import work.lclpnet.ap2.game.maze_scape.gen.test.StringConnector;
import work.lclpnet.ap2.game.maze_scape.gen.test.StringPiece;

import java.util.Arrays;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GraphGeneratorTest {

    @Test
    void test() {
        var start = new StringPiece("""
                ───
                ← →
                ┐↓┌""");

        var pieces = Set.of(new StringPiece("""
                ───
                ← →
                ───"""));

        var random = new Random(100);
        var generator = new String2DGeneratorDomain(pieces, random);
        var graphGen = new GraphGenerator<>(generator, random);

        var graph = graphGen.generateGraph(start, 4);

        assertEquals(1, graph.nodesAtLevel(0).size());
        assertEquals(3, graph.nodesAtLevel(1).size());
        assertEquals(0, graph.nodesAtLevel(2).size());

        String expected = """
                ─────────
                ← →← →← →
                ───┐↓┌───
                   │↑│  \s
                   │ │  \s
                   │↓│  \s""";

        String actual = genString(graph);

        assertEquals(expected, actual);
    }

    private String genString(Graph<StringConnector, StringPiece, OrientedStringPiece> graph) {
        MutableInt minX = new MutableInt(Integer.MAX_VALUE), maxX = new MutableInt(Integer.MIN_VALUE),
                minY = new MutableInt(Integer.MAX_VALUE), maxY = new MutableInt(Integer.MIN_VALUE);

        // determine dimensions
        graph.root().traverse(node -> {
            OrientedStringPiece oriented = node.oriented();

            if (oriented == null) return false;

            int x = oriented.x(), y = oriented.y();

            if (x < minX.intValue()) {
                minX.setValue(x);
            }

            if (y < minY.intValue()) {
                minY.setValue(y);
            }

            int mx = x + oriented.width(), my = y + oriented.height();

            if (mx > maxX.intValue()) {
                maxX.setValue(mx);
            }

            if (my > maxY.intValue()) {
                maxY.setValue(my);
            }

            return true;
        });

        int width = maxX.intValue() - minX.intValue();
        int height = maxY.intValue() - minY.intValue();

        if (width > 100 || height > 100) {
            throw new IllegalStateException("Dimensions to big: %dx%d".formatted(width, height));
        }

        int ox = -Math.min(0, minX.intValue());
        int oy = -Math.min(0, minY.intValue());

        char[][] chars = new char[height][width];

        for (int y = 0; y < height; y++) {
            Arrays.fill(chars[y], ' ');
        }

        graph.root().traverse(node -> {
            OrientedStringPiece oriented = node.oriented();

            if (oriented == null) return false;

            // place oriented piece
            int sx = oriented.x() + ox, sy = oriented.y() + oy;

            String[] lines = oriented.string().split("\n");

            for (int y = 0; y < lines.length; y++) {
                for (int x = 0; x < oriented.width(); x++) {
                    chars[sy + y][sx + x] = lines[y].charAt(x);
                }
            }

            return true;
        });

        return Arrays.stream(chars)
                .map(String::new)
                .collect(Collectors.joining("\n"));
    }
}