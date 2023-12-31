package work.lclpnet.ap2.game.jump_and_run;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameRules;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.map.MapFacade;
import work.lclpnet.ap2.game.jump_and_run.gen.JumpAndRun;
import work.lclpnet.ap2.game.jump_and_run.gen.JumpAndRunSetup;
import work.lclpnet.ap2.impl.game.BootstrapMapOptions;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.ap2.impl.game.data.ScoreTimeDataContainer;

public class JumpAndRunInstance extends DefaultGameInstance {

    private final ScoreTimeDataContainer data = new ScoreTimeDataContainer();
    private JumpAndRunSetup setup;
    private JumpAndRun jumpAndRun;

    public JumpAndRunInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    protected DataContainer getData() {
        return data;
    }

    @Override
    protected void openMap() {
        MapFacade mapFacade = gameHandle.getMapFacade();
        Identifier gameId = gameHandle.getGameInfo().getId();

        mapFacade.openRandomMap(gameId, new BootstrapMapOptions((world, map) -> {
            setup = new JumpAndRunSetup(gameHandle, map, world, 4.0f);  // time in minutes
            return setup.setup().thenAccept(jumpAndRun -> this.jumpAndRun = jumpAndRun);
        }), this::onMapReady);
    }

    @Override
    protected void prepare() {
        MinecraftServer server = gameHandle.getServer();

        GameRules gameRules = getWorld().getGameRules();
        gameRules.get(GameRules.RANDOM_TICK_SPEED).set(0, server);
    }

    @Override
    protected void ready() {

    }
}
