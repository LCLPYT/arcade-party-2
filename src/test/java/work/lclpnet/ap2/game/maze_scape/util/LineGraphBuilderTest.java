package work.lclpnet.ap2.game.maze_scape.util;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.api.ds.UndirectedGraphNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LineGraphBuilderTest {

    @Test
    void buildByNode() {
        var a = new Node('a');
        var b = new Node('b');
        var c = new Node('c');
        var d = new Node('d');

        a.connect(b);
        a.connect(c);
        c.connect(d);

        var passageBuilder = new LineGraphBuilder<Node, Edge>((from, to) -> new Edge(from.ch, to.ch));
        var passages = passageBuilder.buildByNode(a);

        Map<Node, Set<Edge>> expected = Map.of(
                a, Set.of(new Edge('a', 'b'), new Edge('a', 'c')),
                b, Set.of(new Edge('a', 'b')),
                c, Set.of(new Edge('a', 'c'), new Edge('c', 'd')),
                d, Set.of(new Edge('c', 'd')));

        assertEquals(expected, passages.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> Set.copyOf(entry.getValue()))));
    }

    private record Node(char ch, List<Node> neighbours) implements UndirectedGraphNode<Node> {

        Node(char ch) {
            this(ch, new ArrayList<>());
        }

        void connect(Node other) {
            if (!neighbours.contains(other)) {
                neighbours.add(other);
            }

            if (!other.neighbours.contains(this)) {
                other.neighbours.add(this);
            }
        }

        @Override
        public @NotNull List<Node> neighbours() {
            return neighbours;
        }

        @Override
        public String toString() {
            return "Node(%s)".formatted(ch);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Node node = (Node) o;
            return ch == node.ch;
        }

        @Override
        public int hashCode() {
            return ch;
        }
    }

    private record Edge(List<Edge> neighbours, char from, char to) implements UndirectedGraphNode<Edge> {

        Edge(char from, char to) {
            this(new ArrayList<>(), from, to);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Edge edge = (Edge) o;
            return from == edge.from && to == edge.to;
        }

        @Override
        public int hashCode() {
            return from + to;
        }
    }
}