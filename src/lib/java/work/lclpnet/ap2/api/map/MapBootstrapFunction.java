package work.lclpnet.ap2.api.map;

import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.lobby.game.map.GameMap;

public interface MapBootstrapFunction {

    void bootstrapWorld(@NotNull ServerLevel world, @NotNull GameMap map);
}
