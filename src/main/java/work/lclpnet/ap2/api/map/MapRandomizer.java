package work.lclpnet.ap2.api.map;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface MapRandomizer {

    CompletableFuture<GameMap> nextMap(Identifier gameId);

    CompletableFuture<Set<Identifier>> getAvailableMapIds(Identifier gameId);

    void forceMap(@Nullable Identifier mapId);
}
