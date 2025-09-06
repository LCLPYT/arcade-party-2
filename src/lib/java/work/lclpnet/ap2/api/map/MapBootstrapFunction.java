package work.lclpnet.ap2.api.map;

import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.lobby.game.map.GameMap;

public interface MapBootstrapFunction {

    void bootstrapWorld(@NotNull ServerWorld world, @NotNull GameMap map);
}
