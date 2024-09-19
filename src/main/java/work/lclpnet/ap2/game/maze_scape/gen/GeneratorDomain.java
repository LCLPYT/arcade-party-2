package work.lclpnet.ap2.game.maze_scape.gen;

import java.util.List;
import java.util.Random;

public interface GeneratorDomain<C, P extends Piece<C>, O extends OrientedPiece<C, P>> {

    O placeStart(P startPiece);

    List<O> fittingPieces(O oriented, C connector);

    void placePiece(O oriented);

    void removePiece(O oriented);

    default O choosePiece(List<O> fitting, Random random) {
        return fitting.get(random.nextInt(fitting.size()));
    }
}
