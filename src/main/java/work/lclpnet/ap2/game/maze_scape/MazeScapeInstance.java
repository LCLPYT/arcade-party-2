package work.lclpnet.ap2.game.maze_scape;

import net.minecraft.server.world.ServerWorld;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.game.maze_scape.setup.MSGenerator;
import work.lclpnet.ap2.game.maze_scape.setup.MSLoader;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.concurrent.CompletableFuture;

public class MazeScapeInstance extends EliminationGameInstance implements MapBootstrap {

    public MazeScapeInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    public CompletableFuture<Void> createWorldBootstrap(ServerWorld world, GameMap map) {
        var setup = new MSLoader(world, map, gameHandle.getLogger());

        return setup.load().thenAccept(res -> new MSGenerator(world, map, res).generate());
    }

    @Override
    protected void prepare() {

    }

    @Override
    protected void ready() {

    }
}
