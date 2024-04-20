package work.lclpnet.ap2.game.speed_builders;

import net.minecraft.server.world.ServerWorld;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.game.speed_builders.data.SbIsland;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class SpeedBuildersInstance extends EliminationGameInstance implements MapBootstrap {

    private final Random random;
    private final SpeedBuilderSetup setup;
    private SpeedBuildersManager manager = null;

    public SpeedBuildersInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        random = new Random();
        setup = new SpeedBuilderSetup(random, gameHandle.getLogger());
    }

    @Override
    public CompletableFuture<Void> createWorldBootstrap(ServerWorld world, GameMap map) {
        return setup.setup(map, world).thenRun(() -> {
            Participants participants = gameHandle.getParticipants();

            var islands = setup.createIslands(participants, world);

            manager = new SpeedBuildersManager(islands, gameHandle);
        });
    }

    @Override
    protected void prepare() {
        manager.each(SbIsland::teleport);
    }

    @Override
    protected void ready() {

    }
}
