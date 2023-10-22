package work.lclpnet.ap2.api.map;

import net.minecraft.util.Identifier;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.concurrent.CompletableFuture;

public interface MapRandomizer {

    CompletableFuture<GameMap> nextMap(Identifier gameId);
}
