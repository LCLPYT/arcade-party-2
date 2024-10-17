package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.util.math.Position;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.game.maze_scape.gen.Graph;
import work.lclpnet.ap2.game.maze_scape.setup.Connector3;
import work.lclpnet.ap2.game.maze_scape.setup.OrientedStructurePiece;
import work.lclpnet.ap2.game.maze_scape.setup.StructurePiece;

public final class MSStruct {

    private final Graph<Connector3, StructurePiece, OrientedStructurePiece> graph;
    private final CachedGraphDistanceCalculator<Graph.Node<Connector3, StructurePiece, OrientedStructurePiece>> distanceCalculator;

    public MSStruct(Graph<Connector3, StructurePiece, OrientedStructurePiece> graph) {
        this.graph = graph;
        this.distanceCalculator = new CachedGraphDistanceCalculator<>();
    }

    @Nullable
    public Graph.Node<Connector3, StructurePiece, OrientedStructurePiece> nodeAt(Position pos) {
        for (var node : graph.root().iterate()) {
            var oriented = node.oriented();

            if (oriented == null) continue;

            if (oriented.bounds().contains(pos)) {
                return node;
            }
        }

        return null;
    }

    public Graph<Connector3, StructurePiece, OrientedStructurePiece> graph() {
        return graph;
    }

    public CachedGraphDistanceCalculator<Graph.Node<Connector3, StructurePiece, OrientedStructurePiece>> distanceCalculator() {
        return distanceCalculator;
    }
}
