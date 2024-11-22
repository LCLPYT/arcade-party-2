package work.lclpnet.ap2.game.maze_scape.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.ds.UndirectedGraphNode;

import java.util.*;
import java.util.function.BiFunction;

/**
 * A class that builds an undirected line graph from an undirected graph.
 * @implNote This class expects {@link L#neighbours()} to return a mutable {@link List}.
 * @param <N> The node type.
 * @param <L> The line node type.
 * @see <a href="https://en.wikipedia.org/wiki/Line_graph">Line Graph on Wikipedia</a>
 */
public class LineGraphBuilder<N extends UndirectedGraphNode<N>, L extends UndirectedGraphNode<L>> {

    private final BiFunction<N, N, @Nullable L> lineProvider;

    /**
     * Create a LineGraphBuilder.
     * @param lineProvider A function that creates an optional line for two nodes. If there is no line between the two nodes, null should be returned.
     */
    public LineGraphBuilder(BiFunction<N, N, @Nullable L> lineProvider) {
        this.lineProvider = lineProvider;
    }

    public Map<N, List<L>> buildByNode(N root) {
        var queue = new LinkedList<N>();
        var seen = new HashSet<N>();

        queue.add(root);
        seen.add(root);

        var lineIndex = new Int2ObjectOpenHashMap<L>();
        var lineByNode = new HashMap<N, List<L>>();
        var clique = new ArrayList<L>();

        while (!queue.isEmpty()) {
            var node = queue.poll();

            clique.clear();

            for (@Nullable N neighbour : node.neighbours()) {
                if (neighbour == null) continue;

                // only one line for each undirected edge
                int hash = node.hashCode() + neighbour.hashCode();

                L line = lineIndex.computeIfAbsent(hash, _h -> lineProvider.apply(node, neighbour));

                if (line == null) continue;

                clique.add(line);

                if (seen.add(neighbour)) {
                    queue.offer(neighbour);
                }
            }

            // interconnect nodes in current clique
            int k = clique.size();

            for (int i = 0; i < k; i++) {
                for (int j = i + 1; j < k; j++) {
                    L x = clique.get(i);
                    L y = clique.get(j);

                    x.neighbours().add(y);
                    y.neighbours().add(x);
                }
            }

            lineByNode.put(node, List.copyOf(clique));
        }

        return lineByNode;
    }
}
