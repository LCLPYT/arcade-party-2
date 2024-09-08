package work.lclpnet.ap2.game.maze_scape.gen.test;

import work.lclpnet.ap2.game.maze_scape.gen.GeneratorDomain;
import work.lclpnet.kibu.util.math.Matrix3i;

import java.util.*;

public class String2DGeneratorDomain implements GeneratorDomain<StringConnector, StringPiece, OrientedStringPiece> {

    private final Collection<StringPiece> pieces;
    private final Random random;
    private final Set<OrientedStringPiece> placed = new HashSet<>();

    public String2DGeneratorDomain(Collection<StringPiece> pieces, Random random) {
        this.pieces = pieces;
        this.random = random;
    }

    @Override
    public OrientedStringPiece placeStart(StringPiece startPiece) {
        var start = new OrientedStringPiece(startPiece, 0, 0, randomRotation(), -1);

        placePiece(start);

        return start;
    }

    @Override
    public List<OrientedStringPiece> fittingPieces(StringConnector connector) {
        List<OrientedStringPiece> fitting = new ArrayList<>();

        int conX = connector.x(), conY = connector.y();

        for (StringPiece piece : pieces) {
            int baseWidth = piece.width(), baseHeight = piece.height();

            // find all possible placements according to connectors of the piece
            var connectors = piece.connectors();

            for (int i = 0, len = connectors.size(); i < len; i++) {
                StringConnector other = connectors.get(i);

                // determine rotation and position
                int rotation = connector.rotateToFace(other);

                var mat = Matrix3i.makeRotationZ(rotation);

                var dimensions = mat.transform(baseWidth, baseHeight, 0);

                int ox = -Math.min(0, dimensions.getX() + 1);
                int oy = -Math.min(0, dimensions.getY() + 1);

                var pos = mat.transform(other.x(), other.y(), 0);

                int x = conX + connector.directionX() - pos.getX() - ox;
                int y = conY + connector.directionY() - pos.getY() - oy;

                int width = Math.abs(dimensions.getX());
                int height = Math.abs(dimensions.getY());

                // check if piece would fit with rotation and position
                if (hasCollision(x, y, width, height)) continue;

                fitting.add(new OrientedStringPiece(piece, x, y, rotation, i));
            }
        }

        return fitting;
    }

    @Override
    public void placePiece(OrientedStringPiece oriented) {
        placed.add(oriented);
    }

    @Override
    public void removePiece(OrientedStringPiece oriented) {
        placed.remove(oriented);
    }

    private int randomRotation() {
        return random.nextInt(4);
    }

    private boolean hasCollision(int xMin, int yMin, int width, int height) {
        for (OrientedStringPiece piece : placed) {
            if (xMin + width > piece.x() && piece.x() + piece.width() > xMin &&
                yMin + height > piece.y() && piece.y() + piece.height() > yMin) {
                return true;
            }
        }

        return false;
    }
}
