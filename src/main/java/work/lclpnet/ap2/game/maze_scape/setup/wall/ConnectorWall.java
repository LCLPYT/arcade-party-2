package work.lclpnet.ap2.game.maze_scape.setup.wall;

import net.minecraft.server.world.ServerWorld;
import work.lclpnet.ap2.game.maze_scape.setup.Connector3;
import work.lclpnet.ap2.game.maze_scape.setup.OrientedStructurePiece;

import java.util.Random;

public interface ConnectorWall {

    void place(Connector3 connector, OrientedStructurePiece oriented, ServerWorld world, Random random);
}
