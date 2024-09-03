package work.lclpnet.ap2.game.aim_master;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.ap2.impl.game.data.ScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedPlayerBossBar;
import work.lclpnet.ap2.impl.util.world.StackedRoomGenerator;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.Random;
import java.util.concurrent.CompletableFuture;

import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class AimMasterInstance extends DefaultGameInstance implements MapBootstrap {

    private final ScoreDataContainer<ServerPlayerEntity, PlayerRef> data = new ScoreDataContainer<>(PlayerRef::create);

    private static final int MIN_SCORE = 24;
    private static final int MAX_SCORE = 36;

    private static final int TARGET_NUMBER = 5;
    private static final int SPHERE_RADIUS = 14;
    private static final int SPHERE_OFFSET = 3;
    private static final int CONE_FOV = 30;

    private static final Random random = new Random();
    int scoreGoal = MIN_SCORE + random.nextInt(MAX_SCORE - MIN_SCORE + 1);

    private DynamicTranslatedPlayerBossBar bossBar;
    private AimMasterManager manager;

    public AimMasterInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    protected ScoreDataContainer<ServerPlayerEntity, PlayerRef> getData() {
        return data;
    }

    @Override
    public CompletableFuture<Void> createWorldBootstrap(ServerWorld world, GameMap map) {
        var generator = new StackedRoomGenerator<>(world, map, StackedRoomGenerator.Coordinates.ABSOLUTE, (pos, spawn, yaw, structure) -> new AimMasterDomain(pos, spawn, yaw));

        return generator.generate(gameHandle.getParticipants())
                .thenAccept(result -> {
                    var domains = result.rooms();
                    manager = new AimMasterManager(domains, world);

                })
                .exceptionally(throwable -> {
                    gameHandle.getLogger().error("Failed to create domains", throwable);
                    return null;
                });
    }

    @Override
    protected void prepare() {
        ServerWorld world = getWorld();

        PositionGenerator positionGenerator = new PositionGenerator(SPHERE_RADIUS, SPHERE_OFFSET, new BlockPos(0,42,0), world, CONE_FOV, TARGET_NUMBER);

        bossBar = usePlayerDynamicTaskDisplay(styled(scoreGoal, Formatting.YELLOW), styled(0, Formatting.YELLOW));

        //hooks
        HookRegistrar hooks = gameHandle.getHookRegistrar();

    }

    @Override
    protected void ready() {
        var subject = gameHandle.getTranslations().translateText("game.ap2.aim_master.task");
    }

}
