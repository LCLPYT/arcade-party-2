package work.lclpnet.ap2.api.map;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.concurrent.CompletableFuture;

public interface MapRandomizer {

    CompletableFuture<GameMap> nextMap(ResourceLocation gameId);

    void forceMap(@Nullable ResourceLocation mapId);
}
