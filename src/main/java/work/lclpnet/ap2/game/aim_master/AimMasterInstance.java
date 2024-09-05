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
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.Random;
import java.util.concurrent.CompletableFuture;

import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class AimMasterInstance extends DefaultGameInstance implements MapBootstrap {

    private final ScoreDataContainer<ServerPlayerEntity, PlayerRef> data = new ScoreDataContainer<>(PlayerRef::create);

    //game parameters
    private static final int MIN_SCORE = 24;
    private static final int MAX_SCORE = 36;
    private static final int TARGET_NUMBER = 6;
    private static final int TARGET_MIN_DISTANCE = 2;

    //parameters for cone generation
    private static final int SPHERE_RADIUS = 15; //radius of the sphere the cone base is projected on
    private static final int SPHERE_OFFSET = 5; //offset from player position
    private static final double UPWARD_TILT = 0.55; //angle that tilts the view cone upwards
    private static final double ELLIPSE_FACTOR = 0.35; //this factor 'squishes' the base of the view cone to make it elliptical
    private static final int CONE_FOV = 35; //fov of the view cone

    private static final Random random = new Random();
    int scoreGoal = MIN_SCORE + random.nextInt(MAX_SCORE - MIN_SCORE + 1);

    private DynamicTranslatedPlayerBossBar bossBar;
    private AimMasterManager manager;

    private AimMasterSequence sequence;

    public AimMasterInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    protected ScoreDataContainer<ServerPlayerEntity, PlayerRef> getData() {
        return data;
    }

    @Override
    public CompletableFuture<Void> createWorldBootstrap(ServerWorld world, GameMap map) {

        var generator = new StackedRoomGenerator<>(world, map, StackedRoomGenerator.Coordinates.ABSOLUTE, (pos, spawn, yaw, structure) -> new AimMasterDomain(spawn, yaw, world));
        var positionGenerator = new PositionGenerator(SPHERE_RADIUS, SPHERE_OFFSET, UPWARD_TILT, ELLIPSE_FACTOR, new BlockPos(0,0,0), CONE_FOV, TARGET_NUMBER, TARGET_MIN_DISTANCE);
        var blockOptions = new BlockOptions();
        var sequenceGenerator = new SequenceGenerator(positionGenerator, blockOptions, world, scoreGoal);

        sequence = sequenceGenerator.getSequence();

        return generator.generate(gameHandle.getParticipants())
                .thenAccept(result -> {
                    var domains = result.rooms();
                    manager = new AimMasterManager(world, domains, sequence);

                })
                .exceptionally(throwable -> {
                    gameHandle.getLogger().error("Failed to create domains", throwable);
                    return null;
                });
    }

    @Override
    protected void prepare() {

        ServerWorld world = getWorld();

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            AimMasterDomain domain = manager.getDomains().get(player.getUuid());
            domain.teleport(player);
        }

        bossBar = usePlayerDynamicTaskDisplay(styled(scoreGoal, Formatting.YELLOW), styled(0, Formatting.YELLOW));

    }

    @Override
    protected void ready() {

        var subject = gameHandle.getTranslations().translateText("game.ap2.aim_master.task");
        var sequenceItems = sequence.getItems();

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            AimMasterDomain domain = manager.getDomains().get(player.getUuid());
            domain.teleport(player);
            PlayerInventoryAccess.setSelectedSlot(player, 4);
            domain.setBlocks(sequenceItems.getFirst(), player);
        }

        //hooks
        HookRegistrar hooks = gameHandle.getHookRegistrar();
        hooks.registerHook(PlayerInventoryHooks.SLOT_CHANGE, (player, slot) -> {
            if (!(slot == 4)) PlayerInventoryAccess.setSelectedSlot(player, 4);
        });
    }
}
