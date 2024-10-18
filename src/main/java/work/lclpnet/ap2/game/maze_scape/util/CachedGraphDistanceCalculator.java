package work.lclpnet.ap2.game.maze_scape.util;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.game.maze_scape.gen.UndirectedGraphNode;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * A class that calculates the length of the shortest path between two nodes in a graph.
 * @implNote The implementation uses a simple breadth first search (BFS) to find the shortest path between two nodes.
 * All results are cached internally for fast subsequent calls with the same parameters.
 * @param <T> The node type.
 */
public class CachedGraphDistanceCalculator<T extends UndirectedGraphNode<T>> {

    private final Int2IntMap cache = new Int2IntOpenHashMap();

    public int distance(T start, T end) {
        int hash = hash(start, end);

        return cache.computeIfAbsent(hash, _hash -> computeDistance(start, end));
    }

    private int hash(T start, T end) {
        return start.hashCode() + end.hashCode();
    }

    private int computeDistance(T start, T end) {
        // perform BFS search with cycle checking to determine the distance
        Set<T> seen = new HashSet<>();
        seen.add(start);

        Object2IntMap<T> distanceFromStart = new Object2IntOpenHashMap<>();
        distanceFromStart.put(start, 0);

        List<T> queue = new LinkedList<>();
        queue.add(start);

        while (!queue.isEmpty()) {
            T node = queue.removeFirst();

            var children = node.neighbours();

            if (children.isEmpty()) continue;

            int parentDistance = distanceFromStart.getInt(node);

            for (@Nullable T child : children) {
                if (child == null) continue;

                // prevent cycle traversal
                if (seen.add(child)) {
                    queue.add(child);
                }

                // update distance from start
                int distance = parentDistance + 1;

                if (child == end) {
                    return distance;
                }

                distanceFromStart.computeInt(child, (n, old) -> old == null ? distance : Math.min(old, distance));
            }
        }

        return Integer.MAX_VALUE;
    }
}
