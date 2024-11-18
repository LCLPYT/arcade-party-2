package work.lclpnet.ap2.game.maze_scape.util;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.ds.UndirectedGraphNode;

import java.util.*;

public class PassageBuilder<N extends UndirectedGraphNode<N>> {

    private final PosProvider<N> posProvider;

    public PassageBuilder(PosProvider<N> posProvider) {
        this.posProvider = posProvider;
    }

    public Map<N, List<Passage>> build(N root) {
        record Item<N>(N node, @Nullable Passage origin) {}

        var queue = new LinkedList<Item<N>>();
        var seen = new HashSet<N>();

        queue.add(new Item<>(root, null));
        seen.add(root);

        var edgeIndex = new IntOpenHashSet();
        var passages = new HashMap<N, List<Passage>>();
        var clique = new ArrayList<Passage>();

        while (!queue.isEmpty()) {
            var item = queue.poll();

            clique.clear();

            for (@Nullable N neighbour : item.node.neighbours()) {
                if (neighbour == null) continue;

                // only one passage for undirected edge
                if (!edgeIndex.add(item.node.hashCode() + neighbour.hashCode())) continue;

                BlockPos pos = posProvider.get(item.node, neighbour);

                if (pos == null) continue;

                var passage = new Passage(pos);

                clique.add(passage);

                if (seen.add(neighbour)) {
                    queue.offer(new Item<>(neighbour, passage));
                }
            }

            // origin should be part of the clique
            if (item.origin != null) {
                clique.add(item.origin);
            }

            // interconnect nodes in current clique
            int k = clique.size();

            for (int i = 0; i < k; i++) {
                for (int j = i + 1; j < k; j++) {
                    Passage x = clique.get(i);
                    Passage y = clique.get(j);

                    x.neighbours().add(y);
                    y.neighbours().add(x);
                }
            }

            passages.put(item.node, List.copyOf(clique));
        }

        return passages;
    }

    public interface PosProvider<N> {
        @Nullable
        BlockPos get(N from, N to);
    }
}
