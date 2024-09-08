package work.lclpnet.ap2.game.maze_scape.gen;

import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.game.maze_scape.gen.test.String2dGenerator;
import work.lclpnet.ap2.game.maze_scape.gen.test.StringPiece;

import java.util.Random;
import java.util.Set;

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

        var random = new Random();
        var generator = new String2dGenerator(pieces, random);
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
    }
}