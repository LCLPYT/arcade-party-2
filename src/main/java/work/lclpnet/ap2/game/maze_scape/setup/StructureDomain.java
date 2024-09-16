package work.lclpnet.ap2.game.maze_scape.setup;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.game.maze_scape.gen.GeneratorDomain;
import work.lclpnet.ap2.game.maze_scape.util.BVH;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.math.AffineIntMatrix;
import work.lclpnet.kibu.util.math.Matrix3i;

import java.util.*;

public class StructureDomain implements GeneratorDomain<Connector3, StructurePiece, OrientedStructurePiece> {

    private final Collection<StructurePiece> pieces;
    private final Random random;
    private final Object2IntMap<StructurePiece> pieceCount;
    private final BlockBox bounds;
    private final Set<OrientedStructurePiece> placed = new HashSet<>();

    public StructureDomain(Collection<StructurePiece> pieces, Random random, int maxChunkSize, int bottomY, int topY) {
        this.pieces = pieces;
        this.random = random;
        this.pieceCount = new Object2IntOpenHashMap<>(pieces.size());

        int min = -maxChunkSize * 16;
        int max = maxChunkSize * 16 - 1;

        this.bounds = new BlockBox(min, bottomY, min, max, topY, max);
    }

    @Override
    public OrientedStructurePiece placeStart(StructurePiece startPiece) {
        var pos = new BlockPos(0, 64, 0);
        var start = new OrientedStructurePiece(startPiece, pos, randomRotation(), -1, null);

        placePiece(start);

        return start;
    }

    @Override
    public List<OrientedStructurePiece> fittingPieces(OrientedStructurePiece oriented, Connector3 connector) {
        List<OrientedStructurePiece> fitting = new ArrayList<>();

        BlockPos connectorPos = connector.pos();
        StructurePiece originPiece = oriented.piece();
        boolean excludeSame = !originPiece.connectSame();

        for (StructurePiece piece : pieces) {
            if (excludeSame && piece == originPiece) continue;

            int count = pieceCount.getOrDefault(piece, 0);

            if (piece.limitedCount() && count >= piece.maxCount()) continue;

            // find all possible placements according to connectors of the piece
            var connectors = piece.connectors();

            for (int i = 0, len = connectors.size(); i < len; i++) {
                Connector3 otherConnector = connectors.get(i);

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

        return fitting;
    }

    @Override
    public void placePiece(OrientedStructurePiece oriented) {
        placed.add(oriented);
        pieceCount.compute(oriented.piece(), (_piece, count) -> count == null ? 1 : count + 1);
    }

    @Override
    public void removePiece(OrientedStructurePiece oriented) {
        placed.remove(oriented);
        pieceCount.compute(oriented.piece(), (_piece, count) -> count == null ? null : count - 1);
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
}
