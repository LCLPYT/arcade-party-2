package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Position;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.game.maze_scape.gen.Graph;
import work.lclpnet.ap2.game.maze_scape.gen.Node;
import work.lclpnet.ap2.game.maze_scape.setup.Connector3;
import work.lclpnet.ap2.game.maze_scape.setup.OrientedStructurePiece;
import work.lclpnet.ap2.game.maze_scape.setup.StructureDomain;
import work.lclpnet.ap2.game.maze_scape.setup.StructurePiece;
import work.lclpnet.ap2.impl.ds.AStar;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.collision.ChunkedCollisionDetector;
import work.lclpnet.ap2.impl.util.math.Vec2i;

import java.util.*;

import static java.lang.Math.abs;
import static net.minecraft.util.math.ChunkSectionPos.getSectionCoord;

public final class MSStruct {

    private final Graph<Connector3, StructurePiece, OrientedStructurePiece> graph;
    private final CachedGraphDistanceCalculator<Node<Connector3, StructurePiece, OrientedStructurePiece>> distanceCalculator;
    private final AStar<Passage> pathFinder;
    private final StructureDomain.BoundsCfg bounds;
    private final Chunk[][] chunksXZ;
    private final Map<Node<Connector3, StructurePiece, OrientedStructurePiece>, List<Passage>> passages;

    public MSStruct(Graph<Connector3, StructurePiece, OrientedStructurePiece> graph, StructureDomain.BoundsCfg bounds) {
        this.graph = graph;
        this.bounds = bounds;

        distanceCalculator = new CachedGraphDistanceCalculator<>();
        pathFinder = new AStar<>(Passage::estimateDistance, Passage::estimateDistance);

        chunksXZ = buildChunks(graph, bounds);

        var passageBuilder = new PassageBuilder<Node<Connector3, StructurePiece, OrientedStructurePiece>>((from, to) -> {
            var connectors = connectorBetween(from, to);

            if (connectors.isEmpty()) {
                return null;
            }

            return connectors.getFirst().pos().up();
        });

        passages = passageBuilder.build(graph.root());
    }

    private Chunk[][] buildChunks(Graph<Connector3, StructurePiece, OrientedStructurePiece> graph, StructureDomain.BoundsCfg bounds) {
        int size = bounds.maxChunkSize();
        var chunksXZ = new Chunk[size * 2][size * 2];

        for (Chunk[] chunks : chunksXZ) {
            Arrays.fill(chunks, null);
        }

        for (var node : graph.root().iterate()) {
            OrientedStructurePiece oriented = node.oriented();

            if (oriented == null) continue;

            BlockBox box = oriented.bounds().box();

            if (box == null) continue;

            for (Vec2i region : ChunkedCollisionDetector.iterateRegions(box)) {
                int cx = region.x() + size;
                int cz = region.z() + size;

                if (cx < 0 || cx >= size * 2 || cz < 0 || cz >= size * 2) continue;

                Chunk chunk = chunksXZ[cx][cz];

                if (chunk == null) {
                    chunksXZ[cx][cz] = chunk = new Chunk();
                }

                chunk.add(node);
            }
        }

        return chunksXZ;
    }

    @NotNull
    private List<Connector3> connectorBetween(Node<Connector3, StructurePiece, OrientedStructurePiece> from,
                                        Node<Connector3, StructurePiece, OrientedStructurePiece> to) {
        if (to == null) {
            return List.of();
        }

        OrientedStructurePiece fromOriented = from.oriented();
        OrientedStructurePiece toOriented = to.oriented();

        if (fromOriented == null || toOriented == null) {
            return List.of();
        }

        // collect all connectors of "to"
        Set<Connector3> toConnectors = new HashSet<>(toOriented.connectors());

        Connector3 parentConnector = toOriented.parentConnector();

        if (parentConnector != null) {
            toConnectors.add(parentConnector);
        }

        // find connectors of "from" with corresponding opposing connector
        List<Connector3> connectors = new ArrayList<>(1);

        for (Connector3 connector : fromOriented.connectors()) {
            Connector3 opposing = connector.createOpposing();

            if (toConnectors.contains(opposing)) {
                connectors.add(connector);
            }
        }

        // also check parent connector
        parentConnector = fromOriented.parentConnector();

        if (parentConnector != null && toConnectors.contains(parentConnector.createOpposing())) {
            connectors.add(parentConnector);
        }

        return connectors;
    }

    @Nullable
    public Node<Connector3, StructurePiece, OrientedStructurePiece> nodeAt(Position pos) {
        return nodeAt(pos.getX(), pos.getY(), pos.getZ());
    }

    @Nullable
    public Node<Connector3, StructurePiece, OrientedStructurePiece> nodeAt(double x, double y, double z) {
        Chunk chunk = chunkAt(MathHelper.floor(x), MathHelper.floor(z));

        if (chunk == null || chunk.nodes == null) {
            return null;
        }

        for (var node : chunk.nodes) {
            var oriented = node.oriented();

            if (oriented == null) continue;

            if (oriented.bounds().contains(x, y, z)) {
                return node;
            }
        }

        return null;
    }

    @Nullable
    private Chunk chunkAt(int x, int z) {
        int size = bounds.maxChunkSize();
        int cx = getSectionCoord(x) + size;
        int cz = getSectionCoord(z) + size;

        if (cx < 0 || cx >= size * 2 || cz < 0 || cz >= size * 2) {
            return null;
        }

        return chunksXZ[cx][cz];
    }

    public Graph<Connector3, StructurePiece, OrientedStructurePiece> graph() {
        return graph;
    }

    public CachedGraphDistanceCalculator<Node<Connector3, StructurePiece, OrientedStructurePiece>> distanceCalculator() {
        return distanceCalculator;
    }

    @NotNull
    public List<Passage> passagesOf(Node<Connector3, StructurePiece, OrientedStructurePiece> node) {
        var passages = this.passages.get(node);

        if (passages == null) {
            return List.of();
        }

        return passages;
    }

    @Nullable
    public Passage nearestPassageTo(Position pos) {
        var node = nodeAt(pos);

        if (node == null) {
            return null;
        }

        var passages = this.passages.get(node);

        if (passages == null) {
            return null;
        }

        Passage nearest = null;
        double minDist = Double.POSITIVE_INFINITY;

        for (Passage passage : passages) {
            BlockPos p = passage.pos();
            double dist = abs(pos.getX() - p.getX()) + abs(pos.getY() - p.getY()) + abs(pos.getZ() - p.getZ());

            if (dist < minDist) {
                minDist = dist;
                nearest = passage;
            }
        }

        return nearest;
    }

    private static class Chunk {
        List<Node<Connector3, StructurePiece, OrientedStructurePiece>> nodes = null;

        synchronized void add(Node<Connector3, StructurePiece, OrientedStructurePiece> node) {
            if (nodes == null) {
                nodes = new ArrayList<>(1);
            }

            nodes.add(node);
        }
    }
}
