package work.lclpnet.ap2.game.maze_scape;

import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.game.maze_scape.setup.MSGenerator;
import work.lclpnet.ap2.game.maze_scape.setup.MSLoader;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class MazeScapeInstance extends EliminationGameInstance implements MapBootstrap {

    private static final int GENERATOR_MAX_TRIES = 5;

    public MazeScapeInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    public CompletableFuture<Void> createWorldBootstrap(ServerWorld world, GameMap map) {
        Logger logger = gameHandle.getLogger();
        var setup = new MSLoader(world, map, logger);

        return setup.load().thenAccept(res -> {
            long seed = new Random().nextLong();
            var random = new Random(seed);

            var generator = new MSGenerator(world, map, res, random, logger);
            boolean generated = false;

            for (int i = 0; i < GENERATOR_MAX_TRIES; i++) {
                if (generator.generate()) {
                    generated = true;
                    break;
                }

                logger.error("Failed to generate structure. Map: {}, Seed: {}; Retries remaining: {}", map.getDescriptor(), seed, GENERATOR_MAX_TRIES - i - 1);
            }

            if (!generated) {
                logger.error("Failed to generate structure. Maximum number of re-tries has been exceeded");
            }
        });
    }

    @Override
    protected void prepare() {

    }

    @Override
    protected void ready() {

    }
}
