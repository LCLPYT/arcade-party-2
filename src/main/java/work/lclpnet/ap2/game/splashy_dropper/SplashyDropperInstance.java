package work.lclpnet.ap2.game.splashy_dropper;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.api.map.MapBootstrapFunction;
import work.lclpnet.ap2.game.splashy_dropper.data.SdGenerator;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.ap2.impl.game.data.ScoreTimeDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.map.ServerThreadMapBootstrap;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.Random;

public class SplashyDropperInstance extends DefaultGameInstance implements MapBootstrapFunction {

    private static final int MIN_DURATION_SECONDS = 50, MAX_DURATION_SECONDS = 75;
    private final ScoreTimeDataContainer<ServerPlayerEntity, PlayerRef> data = new ScoreTimeDataContainer<>(PlayerRef::create);
    private final Random random = new Random();

    public SplashyDropperInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    protected DataContainer<ServerPlayerEntity, PlayerRef> getData() {
        return data;
    }

    @Override
    protected MapBootstrap getMapBootstrap() {
        // run the bootstrap on the server thread, because the scanWorld method of SdGenerator will run faster
        return new ServerThreadMapBootstrap(this);
    }

    @Override
    public void bootstrapWorld(ServerWorld world, GameMap map) {
        new SdGenerator(world, map, random).generate();
    }

    @Override
    protected void prepare() {
        commons().teleportToRandomSpawns(random);
    }

    @Override
    protected void ready() {
        TranslationService translations = gameHandle.getTranslations();
        var subject = translations.translateText(gameHandle.getGameInfo().getTaskKey());

        int duration = MIN_DURATION_SECONDS + random.nextInt(MAX_DURATION_SECONDS - MIN_DURATION_SECONDS + 1);
        commons().createTimer(subject, duration).whenDone(this::onTimerDone);
    }

    private void onTimerDone() {

    }
}
