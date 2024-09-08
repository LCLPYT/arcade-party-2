package work.lclpnet.ap2.game.maze_scape.gen;

import java.util.List;

public interface GeneratorDomain<C, P extends Piece<C>, O extends OrientedPiece<C, P>> {

    O placeStart(P startPiece);

    List<O> fittingPieces(C connector);

    void placePiece(O oriented);

    void removePiece(O oriented);
}
