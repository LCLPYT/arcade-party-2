package work.lclpnet.ap2.game.maze_scape.util;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Position;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.game.maze_scape.gen.Graph;
import work.lclpnet.ap2.game.maze_scape.gen.Node;
import work.lclpnet.ap2.game.maze_scape.setup.Connector3;
import work.lclpnet.ap2.game.maze_scape.setup.OrientedStructurePiece;
import work.lclpnet.ap2.game.maze_scape.setup.StructureDomain;
import work.lclpnet.ap2.game.maze_scape.setup.StructurePiece;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.collision.ChunkedCollisionDetector;
import work.lclpnet.ap2.impl.util.math.Vec2i;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static net.minecraft.util.math.ChunkSectionPos.getSectionCoord;

public final class MSStruct {

    private final Graph<Connector3, StructurePiece, OrientedStructurePiece> graph;
    private final CachedGraphDistanceCalculator<Node<Connector3, StructurePiece, OrientedStructurePiece>> distanceCalculator;
    private final StructureDomain.BoundsCfg bounds;
    private final Chunk[][] chunksXZ;

    public MSStruct(Graph<Connector3, StructurePiece, OrientedStructurePiece> graph, StructureDomain.BoundsCfg bounds) {
        this.graph = graph;
        this.distanceCalculator = new CachedGraphDistanceCalculator<>();
        this.bounds = bounds;
        this.chunksXZ = buildChunks(graph, bounds);
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

    @Nullable
    public Node<Connector3, StructurePiece, OrientedStructurePiece> nodeAt(Position pos) {
        Chunk chunk = chunkAt(MathHelper.floor(pos.getX()), MathHelper.floor(pos.getZ()));

        if (chunk == null || chunk.nodes == null) {
            return null;
        }

        for (var node : chunk.nodes) {
            var oriented = node.oriented();

            if (oriented == null) continue;

            if (oriented.bounds().contains(pos)) {
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
