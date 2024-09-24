package work.lclpnet.ap2.game.maze_scape.setup;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.game.maze_scape.gen.GeneratorDomain;
import work.lclpnet.ap2.game.maze_scape.gen.NodeView;
import work.lclpnet.ap2.game.maze_scape.util.BVH;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.WeightedList;
import work.lclpnet.ap2.impl.util.math.AffineIntMatrix;
import work.lclpnet.kibu.util.math.Matrix3i;

import java.util.*;

public class StructureDomain implements GeneratorDomain<Connector3, StructurePiece, OrientedStructurePiece> {

    private final Random random;
    private final int deadEndStartLevel;
    private final Object2IntMap<StructurePiece> pieceCount;
    private final BlockBox bounds;
    private final List<StructurePiece> normalPieces, endPieces;
    private final List<OrientedStructurePiece> fitting;
    private final Set<OrientedStructurePiece> placed = new HashSet<>();
    private final WeightedList<OrientedStructurePiece> weightedPieces = new WeightedList<>();
    private int totalArea = 0;
    private boolean onlyDeadEnds = false;

    public StructureDomain(Collection<StructurePiece> pieces, Random random, int deadEndStartLevel, BoundsCfg bounds) {
        this.random = random;
        this.pieceCount = new Object2IntOpenHashMap<>(pieces.size());

        this.endPieces = pieces.stream().filter(StructurePiece::deadEnd).toList();
        this.normalPieces = pieces.stream().filter(piece -> !piece.deadEnd()).toList();
        this.fitting = new ArrayList<>(pieces.size());
        this.deadEndStartLevel = deadEndStartLevel;

        int min = -bounds.maxChunkSize * 16;
        int max = bounds.maxChunkSize * 16 - 1;

        this.bounds = new BlockBox(min, bounds.bottomY, min, max, bounds.topY, max);
    }

    @Override
    public OrientedStructurePiece placeStart(StructurePiece startPiece) {
        var pos = new BlockPos(0, 64, 0);
        var start = new OrientedStructurePiece(startPiece, pos, randomRotation(), -1, null);

        placePiece(start);

        return start;
    }

    @Override
    public List<OrientedStructurePiece> fittingPieces(OrientedStructurePiece oriented, Connector3 connector, NodeView node) {
        fitting.clear();

        int nodeLevel = node.level();

        Cluster cluster = oriented.cluster();

        if (cluster != null && !cluster.complete()) {
            // the parent piece is inside an unfinished cluster, try to expand it first
            addFittingPieces(oriented, connector, cluster.definition().pieces(), nodeLevel);

            if (!fitting.isEmpty()) {
                // mark each fitting piece as potential part of the cluster
                for (var piece : fitting) {
                    piece.setCluster(cluster);
                }

                return fitting;
            }
        }

        // non-cluster behaviour
        if (!onlyDeadEnds) {
            addFittingPieces(oriented, connector, normalPieces, nodeLevel);
        }

        // try to fit dead ends when minimum target level is reached or if no other piece fits
        if (nodeLevel >= deadEndStartLevel || fitting.isEmpty()) {
            addFittingPieces(oriented, connector, endPieces, nodeLevel);
        }

        return fitting;
    }

    private void addFittingPieces(OrientedStructurePiece oriented, Connector3 connector, Collection<StructurePiece> pool, int nodeLevel) {
        BlockPos connectorPos = connector.pos();
        StructurePiece originPiece = oriented.piece();
        boolean excludeSame = !originPiece.connectSame();
        String target = connector.target();
        int distance = nodeLevel + 1;

        for (StructurePiece piece : pool) {
            if (excludeSame && piece == originPiece || distance < piece.minDistance()) continue;

            int count = pieceCount.getOrDefault(piece, 0);

            if (piece.limitedCount() && count >= piece.maxCount()) continue;

            // find all possible placements according to connectors of the piece
            var connectors = piece.connectors();

            for (int i = 0, len = connectors.size(); i < len; i++) {
                Connector3 otherConnector = connectors.get(i);

                // check if otherConnector has the target name
                if (!target.equals(otherConnector.name())) continue;

                // determine rotation and position
                int rotation = connector.rotateToFace(otherConnector);
                var mat = Matrix3i.makeRotationY(rotation);

                var rotatedOtherPos = mat.transform(otherConnector.pos());
                BlockPos pos = connectorPos.add(connector.direction()).subtract(rotatedOtherPos);

                BVH bvh = piece.bounds().transform(new AffineIntMatrix(mat, pos));

                // check if piece would fit with rotation and position
                if (!bvh.isContainedWithin(bounds) || hasCollision(bvh)) continue;

                fitting.add(new OrientedStructurePiece(piece, pos, rotation, i, bvh));
            }
        }
    }

    @Override
    public void placePiece(OrientedStructurePiece oriented) {
        placed.add(oriented);
        pieceCount.compute(oriented.piece(), (_piece, count) -> count == null ? 1 : count + 1);
        totalArea += area(oriented);

        processCluster(oriented);
    }

    @Override
    public void removePiece(OrientedStructurePiece oriented) {
        placed.remove(oriented);
        pieceCount.compute(oriented.piece(), (_piece, count) -> count == null ? null : count - 1);
        totalArea -= area(oriented);

        Cluster cluster = oriented.cluster();

        if (cluster != null) {
            cluster.remove(oriented);
            oriented.setCluster(null);
        }
    }

    @Override
    public synchronized OrientedStructurePiece choosePiece(List<OrientedStructurePiece> fitting, Random random) {
        weightedPieces.clear();

        for (OrientedStructurePiece oriented : fitting) {
            weightedPieces.add(oriented, oriented.piece().weight());
        }

        return weightedPieces.getRandomElement(random);
    }

    private void processCluster(OrientedStructurePiece oriented) {
        Cluster assignedCluster = oriented.cluster();

        if (assignedCluster != null) {
            // actually join the cluster
            assignedCluster.add(oriented);
            return;
        }

        // begin clusters by chance
        for (ClusterDef clusterDef : oriented.piece().clusters()) {
            if (random.nextFloat() >= clusterDef.chance()) continue;

            // begin new cluster instance
            int targetPieceCount = random.nextInt(clusterDef.minPieces(), clusterDef.maxPieces() + 1);
            var cluster = new Cluster(clusterDef, targetPieceCount);

            cluster.add(oriented);
            oriented.setCluster(cluster);

            break;
        }
    }

    private int randomRotation() {
        return random.nextInt(4);
    }

    private boolean hasCollision(BVH bvh) {
        for (OrientedStructurePiece placed : placed) {
            if (bvh.intersects(placed.bounds())) {
                return true;
            }
        }

        return false;
    }

    public int totalArea() {
        return totalArea;
    }

    private static int area(OrientedStructurePiece oriented) {
        BVH bounds = oriented.bounds();
        return bounds.width() * bounds.length();
    }

    public void setOnlyDeadEnds(boolean onlyDeadEnds) {
        this.onlyDeadEnds = onlyDeadEnds;
    }

    public void reset() {
        pieceCount.clear();
        placed.clear();
        totalArea = 0;
        onlyDeadEnds = false;
    }

    public record BoundsCfg(int maxChunkSize, int bottomY, int topY) {}
}
