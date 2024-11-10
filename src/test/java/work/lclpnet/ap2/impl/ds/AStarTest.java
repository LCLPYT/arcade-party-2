package work.lclpnet.ap2.impl.ds;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.api.ds.UndirectedGraphNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AStarTest {

    private AStar<TestNode> aStar;

    @BeforeEach
    public void setUp() {
        AStar.Heuristic<TestNode> heuristic = (a, b) -> Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
        AStar.DistanceCalc<TestNode> distanceCalc = (a, b) -> Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
        aStar = new AStar<>(heuristic, distanceCalc);
    }

    @Test
    public void testFindPathConnectedNodes() {
        var start = new TestNode(0, 0);
        var middle = new TestNode(1, 1);
        var end = new TestNode(2, 2);

        start.connect(middle);
        middle.connect(end);

        var path = aStar.findPath(start, end);

        assertEquals(List.of(start, middle, end), path);
    }

    @Test
    public void testFindPathDisconnectedNodes() {
        var start = new TestNode(0, 0);
        var end = new TestNode(2, 2);

        var path = aStar.findPath(start, end);

        assertEquals(List.of(), path, "Expected no path between disconnected nodes");
    }

    @Test
    public void testFindPathComplexGraph() {
        var start = new TestNode(0, 0);
        var n1 = new TestNode(4, 0);
        var n2 = new TestNode(1, 1);
        var n3 = new TestNode(0, 1);
        var n4 = new TestNode(5, 0);
        var end = new TestNode(5, 1);

        start.connect(n1);
        n1.connect(n2);
        n1.connect(n4);
        n2.connect(end);
        n3.connect(n2);
        n4.connect(n2);

        var path = aStar.findPath(start, end);

        assertEquals(List.of(start, n1, n2, end), path);
    }

    @Test
    public void testFindPathSingleNode() {
        var start = new TestNode(0, 0);
        var path = aStar.findPath(start, start);

        assertEquals(List.of(start), path);
    }

    @Test
    public void testFindPathUnreachableNodesInLargeGraph() {
        var start = new TestNode(0, 0);
        var n1 = new TestNode(1, 0);
        var n2 = new TestNode(2, 0);
        var end = new TestNode(3, 0);

        start.connect(n1);
        n1.connect(n2);

        var path = aStar.findPath(start, end);

        assertEquals(List.of(), path, "Expected no path between nodes in different components");
    }

    @Test
    public void testFindPathWithHeuristicGuidance() {
        var start = new TestNode(0, 0);
        var n1 = new TestNode(1, 0);
        var n2 = new TestNode(1, 1);
        var n3 = new TestNode(2, 1);
        var end = new TestNode(3, 1);

        start.connect(n1);
        n1.connect(n2);
        n2.connect(end);

        start.connect(n3);
        n3.connect(end);

        var path = aStar.findPath(start, end);

        assertEquals(Arrays.asList(start, n3, end), path);
    }

    private static class TestNode implements UndirectedGraphNode<TestNode> {
        final int x, y;
        final List<TestNode> neighbours;

        TestNode(int x, int y) {
            this.x = x;
            this.y = y;
            this.neighbours = new ArrayList<>();
        }

        void connect(TestNode node) {
            this.neighbours.add(node);
            node.neighbours.add(this);
        }

        @Override
        public @NotNull List<TestNode> neighbours() {
            return neighbours;
        }

        @Override
        public String toString() {
            return "(%d, %d)".formatted(x, y);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TestNode testNode = (TestNode) o;
            return x == testNode.x && y == testNode.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }
}