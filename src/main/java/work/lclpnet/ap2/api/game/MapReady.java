package work.lclpnet.ap2.api.game;

import net.minecraft.server.world.ServerWorld;
import work.lclpnet.lobby.game.map.GameMap;

@FunctionalInterface
public interface MapReady {

    void onReady(ServerWorld world, GameMap map);
}
