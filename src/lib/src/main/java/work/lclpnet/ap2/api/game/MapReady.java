package work.lclpnet.ap2.api.game;

import net.minecraft.server.level.ServerLevel;
import work.lclpnet.game.map.GameMap;

@FunctionalInterface
public interface MapReady {

    void onReady(ServerLevel world, GameMap map);
}
