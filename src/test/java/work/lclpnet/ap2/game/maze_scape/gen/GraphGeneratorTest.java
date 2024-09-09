package work.lclpnet.ap2.game.maze_scape.gen;

import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.game.maze_scape.gen.test.String2DGeneratorDomain;
import work.lclpnet.ap2.game.maze_scape.gen.test.StringPiece;
import work.lclpnet.ap2.game.maze_scape.gen.test.TestGraphBuilder;

import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static work.lclpnet.ap2.game.maze_scape.gen.test.GraphStringSerializer.genString;

class GraphGeneratorTest {

    @Test
    void generateGraph_oneLevel() {
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
                   │↑│  \s
                   │ │  \s
                   │↓│  \s
                ───┘↑└───
                ← →← →← →
                ─────────""";

        String actual = genString(graph);

        assertEquals(expected, actual);
    }

    @Test
    void generateGraph_multipleParts() {
        var start = new StringPiece("""
                ───
                ← →
                ┐↓┌""");

        var pieces = List.of(new StringPiece("""
                ───
                ← →
                ───"""), new StringPiece("""
                ──┐
                ← │
                ┐↓│"""));

        var random = new Random(2450);
        var generator = new String2DGeneratorDomain(pieces, random);
        var graphGen = new GraphGenerator<>(generator, random);

        var graph = graphGen.generateGraph(start, 10);

        var expected = """
                   ┌──     \s
                   │ →     \s
                   │↓┌     \s
                   │↑│     \s
                   │ │     \s
                   │↓│     \s
                   │↑│┌─────
                   │ ││ →← →
                   │↓││↓┌───
                │↑││↑└┘↑│  \s
                │ ││ →← │  \s
                │↓││↓┌──┘  \s
                │↑└┘↑│     \s
                │ →← │     \s
                └────┘     \s""";

        var actual = genString(graph);

        assertEquals(expected, actual);
    }

    @Test
    void generateGraph_deadEnd() {
        var straight = new StringPiece("""
                ───
                ← →
                ───""");

        var uTurn = new StringPiece("""
                ┌──
                │ →
                │ ┌
                │ └
                │ →
                └──""");

        var uTurnFlipped = new StringPiece("""
                ──┐
                ← │
                ┐ │
                ┘ │
                ← │
                ──┘""");

        var turn = new StringPiece("""
                ──┐
                ← │
                ┐↓│""");

        var pieces = List.of(uTurn, uTurnFlipped, turn, straight);

        long seed = new Random().nextLong(100_000);
        var random = new Random(seed);
        var domain = new String2DGeneratorDomain(pieces, random);
        var graphGen = new GraphGenerator<>(domain, random);

        // build graph manually
        var builder = new TestGraphBuilder(domain, graphGen);
        var start = builder.start(straight, 0, 0, 0);
        var left = builder.choose(start, 0, uTurn, 0);
        builder.choose(start, 1, uTurnFlipped, 0);
        builder.choose(left, 0, straight, 0);

        var graph = new Graph<>(start);

        graphGen.generateGraph(graph, 4, 2, 5);

        var actual = genString(graph);

        System.out.println(actual);
    }
}