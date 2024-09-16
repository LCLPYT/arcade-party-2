package work.lclpnet.ap2.game.maze_scape.setup.wall;

import work.lclpnet.ap2.game.maze_scape.setup.Connector3;
import work.lclpnet.ap2.game.maze_scape.setup.OrientedStructurePiece;
import work.lclpnet.lobby.util.WorldModifier;

import java.util.Random;

public interface ConnectorWall {

    void place(Connector3 connector, OrientedStructurePiece oriented, WorldModifier modifier, Random random);
}
