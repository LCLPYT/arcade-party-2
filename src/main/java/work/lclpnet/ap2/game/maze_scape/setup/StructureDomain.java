package work.lclpnet.ap2.game.maze_scape.setup;

import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.game.maze_scape.gen.GeneratorDomain;
import work.lclpnet.ap2.game.maze_scape.util.BVH;
import work.lclpnet.ap2.impl.util.math.AffineIntMatrix;
import work.lclpnet.kibu.util.math.Matrix3i;

import java.util.*;

public class StructureDomain implements GeneratorDomain<Connector3, StructurePiece, OrientedStructurePiece> {

    private final Collection<StructurePiece> pieces;
    private final Random random;
    private final Set<OrientedStructurePiece> placed = new HashSet<>();

    public StructureDomain(Collection<StructurePiece> pieces, Random random) {
        this.pieces = pieces;
        this.random = random;
    }

    @Override
    public OrientedStructurePiece placeStart(StructurePiece startPiece) {
        var start = new OrientedStructurePiece(startPiece, BlockPos.ORIGIN, randomRotation(), -1, null);

        placePiece(start);

        return start;
    }

    @Override
    public List<OrientedStructurePiece> fittingPieces(Connector3 connector) {
        List<OrientedStructurePiece> fitting = new ArrayList<>();

        BlockPos connectorPos = connector.pos();

        for (StructurePiece piece : pieces) {
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
                if (hasCollision(bvh)) continue;

                fitting.add(new OrientedStructurePiece(piece, pos, rotation, i, bvh));
            }
        }

        return fitting;
    }

    @Override
    public void placePiece(OrientedStructurePiece oriented) {
        placed.add(oriented);
    }

    @Override
    public void removePiece(OrientedStructurePiece oriented) {
        placed.remove(oriented);
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
