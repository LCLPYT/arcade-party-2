package work.lclpnet.ap2.api.map;

import net.minecraft.util.Identifier;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.Optional;

public interface MapRandomizer {

    Optional<GameMap> nextMap(Identifier gameId);
}
