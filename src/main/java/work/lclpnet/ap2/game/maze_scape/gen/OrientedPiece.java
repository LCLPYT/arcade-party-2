package work.lclpnet.ap2.game.maze_scape.gen;

import java.util.List;

public interface OrientedPiece<C, P extends Piece<C>> {

    P piece();

    List<C> connectors();
}
