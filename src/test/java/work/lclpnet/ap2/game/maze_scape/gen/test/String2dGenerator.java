package work.lclpnet.ap2.game.maze_scape.gen.test;

import work.lclpnet.ap2.game.maze_scape.gen.Generator;
import work.lclpnet.kibu.util.math.Matrix3i;

import java.util.*;

public class String2dGenerator implements Generator<StringConnector, StringPiece, OrientedStringPiece> {

    private final Set<StringPiece> pieces;
    private final Random random;
    private final Set<OrientedStringPiece> placed = new HashSet<>();

    public String2dGenerator(Set<StringPiece> pieces, Random random) {
        this.pieces = pieces;
        this.random = random;
    }

    @Override
    public OrientedStringPiece placeStart(StringPiece startPiece) {
        var start = new OrientedStringPiece(startPiece, 0, 0, randomRotation());

        placed.add(start);

        return start;
    }

    @Override
    public List<OrientedStringPiece> fittingPieces(StringConnector connector) {
        List<OrientedStringPiece> fitting = new ArrayList<>();

        int conX = connector.x(), conY = connector.y();

        for (StringPiece piece : pieces) {
            int baseWidth = piece.width(), baseHeight = piece.height();

            // find all possible placements according to connectors of the piece
            for (StringConnector other : piece.connectors()) {
                // determine rotation and position
                int rotation = connector.rotateToFace(other);

                var mat = Matrix3i.makeRotationZ(rotation);

                var dimensions = mat.transform(baseWidth, baseHeight, 0);

                int ox = -Math.min(0, dimensions.getX() + 1);
                int oy = -Math.min(0, dimensions.getY() + 1);

                var pos = mat.transform(other.x(), other.y(), 0);

                int x = conX + connector.directionX() - pos.getX() - ox;
                int y = conY + connector.directionY() - pos.getY() - oy;

                // check if piece would fit with rotation and position // TODO implement

                fitting.add(new OrientedStringPiece(piece, x, y, rotation));
            }
        }

        return fitting;
    }

    private int randomRotation() {
        return random.nextInt(4);
    }
}
