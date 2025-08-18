package work.lclpnet.ap2.api.map;

import net.minecraft.server.world.ServerWorld;
import work.lclpnet.lobby.game.map.GameMap;

public interface MapBootstrapFunction {

    void bootstrapWorld(ServerWorld world, GameMap map);
}
