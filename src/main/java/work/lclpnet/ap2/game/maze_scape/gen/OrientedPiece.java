package work.lclpnet.ap2.game.maze_scape.gen;

import java.util.List;

/**
 * An oriented piece represents a piece of type {@link P} that is placed in the world.
 * That means implementations should store something like position and orientation.
 * @implSpec Implementations must implement the {@link Object#equals(Object)} method properly.
 * It is assumed that different instances with the same data are identified as equal, e.g. by the {@link GraphGenerator} algorithm for back-tracking.
 * @param <C> The connector type.
 * @param <P> The base piece type.
 */
public interface OrientedPiece<C, P extends Piece<C>> {

    P piece();

    List<C> connectors();
}
