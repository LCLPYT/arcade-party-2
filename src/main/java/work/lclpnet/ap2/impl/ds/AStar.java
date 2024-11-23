package work.lclpnet.ap2.impl.ds;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.ds.UndirectedGraphNode;

import java.util.*;

public class AStar<N extends UndirectedGraphNode<N>> {

    private final Heuristic<N> heuristic;
    private final DistanceCalc<N> distanceCalc;

    public AStar(Heuristic<N> heuristic, DistanceCalc<N> distanceCalc) {
        this.heuristic = heuristic;
        this.distanceCalc = distanceCalc;
    }

    @NotNull
    public List<N> findPath(N start, N end) {
        Map<N, Node<N>> nodes = new HashMap<>();

        var startNode = node(start, nodes);
        var endNode = node(end, nodes);

        var queue = new MinHeap<Node<N>>();
        Set<N> open = new HashSet<>();
        Set<N> closed = new HashSet<>();

        startNode.g = 0;
        startNode.h = heuristic.estimate(start, end);
        startNode.update();

        queue.offer(startNode);
        open.add(start);

        while (!queue.isEmpty()) {
            var current = queue.poll();

            assert current != null;

            if (current.equals(endNode)) {
                return makePath(current);
            }

            open.remove(current.node);
            closed.add(current.node);

            for (N neighbour : current.node.neighbours()) {
                if (closed.contains(neighbour)) continue;

                var neighbourNode = node(neighbour, nodes);

                double g = current.g + distanceCalc.distance(current.node, neighbour);

                if (g >= neighbourNode.g) continue;

                neighbourNode.g = g;
                neighbourNode.h = heuristic.estimate(neighbour, end);
                neighbourNode.update();

                neighbourNode.parent = current;
                neighbourNode.level = current.level + 1;

                if (open.add(neighbour)) {
                    queue.offer(neighbourNode);
                } else {
                    queue.update(neighbourNode);
                }
            }
        }

        return List.of();
    }

    private List<N> makePath(Node<N> current) {
        List<N> path = new ArrayList<>(current.level + 1);
        var node = current;

        while (node != null) {
            path.add(node.node);
            node = node.parent;
        }

        Collections.reverse(path);

        return path;
    }

    @NotNull
    private Node<N> node(N neighbour, Map<N, Node<N>> nodes) {
        return nodes.computeIfAbsent(neighbour, Node::new);
    }

    public interface Heuristic<N extends UndirectedGraphNode<N>> {

        double estimate(N a, N b);
    }

    public interface DistanceCalc<N extends UndirectedGraphNode<N>> {

        double distance(N a, N b);
    }

    private static class Node<N extends UndirectedGraphNode<N>> implements Comparable<Node<N>> {

        final N node;
        double g = Double.POSITIVE_INFINITY, h = 0, f = 0;
        @Nullable AStar.Node<N> parent = null;
        int level = 0;

        Node(N node) {
            this.node = node;
        }

        void update() {
            f = g + h;
        }

        @Override
        public int compareTo(@NotNull AStar.Node<N> o) {
            return Double.compare(f, o.f);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Node<?> node = (Node<?>) o;
            return Objects.equals(this.node, node.node);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(node);
        }
    }
}
