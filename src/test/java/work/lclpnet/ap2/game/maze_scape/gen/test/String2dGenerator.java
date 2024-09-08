package work.lclpnet.ap2.game.maze_scape.gen.test;

import work.lclpnet.ap2.game.maze_scape.gen.Generator;

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

        int cx = connector.x(), cy = connector.y();

        for (StringPiece piece : pieces) {
            // find all possible placements according to connectors of the piece
            for (StringConnector other : piece.connectors()) {
                // determine orientation and position

            }
        }

        return fitting;
    }

    private int randomRotation() {
        return random.nextInt(4);
    }
}
