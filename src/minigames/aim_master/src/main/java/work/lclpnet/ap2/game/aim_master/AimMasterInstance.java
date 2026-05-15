package work.lclpnet.ap2.game.aim_master;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.impl.game.FFAGameInstance;
import work.lclpnet.ap2.impl.game.data.IntScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedPlayerBossBar;
import work.lclpnet.ap2.impl.util.world.StackedRoomGenerator;
import work.lclpnet.game.map.GameMap;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.access.entity.ServerPlayerAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks;
import work.lclpnet.kibu.hook.player.PlayerSwingHandHook;
import work.lclpnet.kibu.scheduler.api.RunningTask;
import work.lclpnet.kibu.scheduler.api.SchedulerAction;
import work.lclpnet.kibu.scheduler.api.TaskHandle;

import java.util.Random;
import java.util.concurrent.CompletableFuture;

import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class AimMasterInstance extends FFAGameInstance implements MapBootstrap {

    private final IntScoreDataContainer<ServerPlayer, PlayerRef> data = new IntScoreDataContainer<>(PlayerRef::create);

    //game parameters
    private static final int MIN_SCORE = 18;
    private static final int MAX_SCORE = 28;
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
    protected IntScoreDataContainer<ServerPlayer, PlayerRef> getData() {
        return data;
    }

    @Override
    public @NotNull CompletableFuture<Void> createWorldBootstrap(@NotNull ServerLevel world, @NotNull GameMap map) {

        var generator = new StackedRoomGenerator<>(world, map, StackedRoomGenerator.Coordinates.RELATIVE, (_, spawn, yaw, _) -> new AimMasterDomain(spawn, yaw, world));
        var positionGenerator = new PositionGenerator(SPHERE_RADIUS, SPHERE_OFFSET, UPWARD_TILT, ELLIPSE_FACTOR, new BlockPos(0, 0, 0), CONE_FOV, TARGET_NUMBER, TARGET_MIN_DISTANCE);
        var blockOptions = new BlockOptions();
        var sequenceGenerator = new SequenceGenerator(positionGenerator, blockOptions, scoreGoal);

        sequence = sequenceGenerator.getSequence();

        return generator.generate(gameHandle.getParticipants())
                .thenAccept(result -> {
                    var domains = result.rooms();
                    manager = new AimMasterManager(domains, sequence);

                })
                .exceptionally(throwable -> {
                    gameHandle.getLogger().error("Failed to create domains", throwable);
                    return null;
                });
    }

    @Override
    protected void prepare() {

        for (ServerPlayer player : gameHandle.getParticipants()) {
            AimMasterDomain domain = manager.getDomains().get(player.getUUID());
            domain.teleport(player);
        }
        bossBar = usePlayerDynamicTaskDisplay(styled(scoreGoal, ChatFormatting.YELLOW));
        bossBar.setPercent(0);
    }

    @Override
    protected void go() {

        var sequenceItems = sequence.getItems();

        for (ServerPlayer player : gameHandle.getParticipants()) {
            AimMasterDomain domain = manager.getDomains().get(player.getUUID());
            domain.teleport(player);
            PlayerInventoryAccess.setSelectedSlot(player, 4);
            domain.setBlocks(sequenceItems.getFirst(), player);
        }

        //hooks
        HookRegistrar hooks = gameHandle.getHooks();
        PlayerInventoryHooks.SLOT_CHANGE.registerWith(hooks, (player, slot) -> {
            if (!(slot == 4)) PlayerInventoryAccess.setSelectedSlot(player, 4);
        });

        PlayerInteractionHooks.USE_ITEM.registerWith(hooks, (player, _, _) -> invokeRayCaster(player));
        PlayerSwingHandHook.HOOK.registerWith(hooks, (player, _) -> invokeRayCaster(player));
    }

    private @NotNull InteractionResult invokeRayCaster(Player player) {
        if (winManager.isGameOver()) return InteractionResult.FAIL;

        AimMasterDomain domain = manager.getDomains().get(player.getUUID());
        ServerPlayer serverPlayer = (ServerPlayer) player;

        if (domain.rayCaster(serverPlayer, SPHERE_RADIUS)) {

            data.addScore(serverPlayer, 1);
            int newScore = data.getScore(serverPlayer);
            bossBar.getBossBar(serverPlayer).setProgress((float) newScore / scoreGoal);

            BlockPos target = domain.getCurrentTarget();
            ServerLevel serverWorld = serverPlayer.level();

            if (target!=null) serverWorld.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY(), target.getZ(), 12, 0.4, 0.4, 0.4, 0.01);
            ServerPlayerAccess.playSoundToPlayer(serverPlayer, SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.5f, 0.8f);

            if (newScore >= scoreGoal) win(serverPlayer);
            else manager.advancePlayer(serverPlayer);

            return InteractionResult.FAIL;
        }

        ServerPlayerAccess.playSoundToPlayer(serverPlayer, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.3f, 0.2f);

        return InteractionResult.PASS;
    }

    protected void win(ServerPlayer winner) {
        var domain = manager.getDomains().get(winner.getUUID());

        domain.removeBlocks(sequence.getItems().getLast());

        //play victory animation
        Task task = new Task(winner, domain, sequence);
        TaskHandle taskHandle = gameHandle.getRootScheduler().interval(task, 5);

        winManager.complete().then(taskHandle::cancel);
    }

    private static class Task implements SchedulerAction {

        private final ServerPlayer player;
        private final AimMasterDomain domain;
        private final AimMasterSequence sequence;
        int time = 0;

        private Task(ServerPlayer player, AimMasterDomain domain, AimMasterSequence sequence) {
            this.player = player;
            this.domain = domain;
            this.sequence = sequence;
        }

        @Override
        public void run(RunningTask info) {

            int i = time / 2;
            var sequenceItem = sequence.getItems().get(sequence.getItems().size() - 1 - i);

            if (time % 2 == 0) domain.setBlocks(sequenceItem, player);
            else domain.removeBlocks(sequenceItem);

            if (i >= sequence.getItems().size() - 1) info.cancel();
            time++;
        }
    }
}
