package work.lclpnet.ap2.game.maze_scape.gen;

import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.game.maze_scape.gen.test.*;

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

        long seed = -5313774123164251313L; // new Random().nextLong(10_000);
        System.out.println("Seed: " + seed);

        var random = new Random(seed);
        var generator = new String2DGeneratorDomain(pieces, random);
        var graphGen = new GraphGenerator<>(generator, random);

        var graph = graphGen.generateGraph(start, 10);

        var string = genString(graph);
        System.out.println(string);
    }
}