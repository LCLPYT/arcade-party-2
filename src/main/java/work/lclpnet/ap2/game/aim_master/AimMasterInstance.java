package work.lclpnet.ap2.game.aim_master;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.ap2.impl.game.data.ScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedPlayerBossBar;
import work.lclpnet.ap2.impl.util.world.StackedRoomGenerator;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks;
import work.lclpnet.kibu.hook.player.PlayerSwingHandHook;
import work.lclpnet.kibu.scheduler.api.RunningTask;
import work.lclpnet.kibu.scheduler.api.SchedulerAction;
import work.lclpnet.kibu.scheduler.api.TaskHandle;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.Random;
import java.util.concurrent.CompletableFuture;

import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class AimMasterInstance extends DefaultGameInstance implements MapBootstrap {

    private final ScoreDataContainer<ServerPlayerEntity, PlayerRef> data = new ScoreDataContainer<>(PlayerRef::create);

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
    protected ScoreDataContainer<ServerPlayerEntity, PlayerRef> getData() {
        return data;
    }

    @Override
    public CompletableFuture<Void> createWorldBootstrap(ServerWorld world, GameMap map) {

        var generator = new StackedRoomGenerator<>(world, map, StackedRoomGenerator.Coordinates.ABSOLUTE, (pos, spawn, yaw, structure) -> new AimMasterDomain(spawn, yaw, world));
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

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            AimMasterDomain domain = manager.getDomains().get(player.getUuid());
            domain.teleport(player);
        }
        bossBar = usePlayerDynamicTaskDisplay(styled(scoreGoal, Formatting.YELLOW));
        bossBar.setPercent(0);
    }

    @Override
    protected void ready() {

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

        hooks.registerHook(PlayerInteractionHooks.USE_ITEM, (player, world, hand) -> invokeRayCaster(player));
        hooks.registerHook(PlayerSwingHandHook.HOOK, (player, hand) -> invokeRayCaster(player));
    }

    private @NotNull TypedActionResult<ItemStack> invokeRayCaster(PlayerEntity player) {
        AimMasterDomain domain = manager.getDomains().get(player.getUuid());
        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;

        if (domain.rayCaster(serverPlayer, SPHERE_RADIUS)) {

            data.addScore(serverPlayer, 1);
            int newScore = data.getScore(serverPlayer);
            bossBar.getBossBar(serverPlayer).setPercent((float) newScore / scoreGoal);

            BlockPos target = domain.getCurrentTarget();
            ServerWorld serverWorld = serverPlayer.getServerWorld();

            if (target!=null) serverWorld.spawnParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY(), target.getZ(), 12, 0.4, 0.4, 0.4, 0.01);
            player.playSoundToPlayer(SoundEvents.ENTITY_ARROW_HIT_PLAYER, SoundCategory.PLAYERS, 0.5f, 0.8f);

            if (newScore >= scoreGoal) win(serverPlayer);
            else manager.advancePlayer(serverPlayer);

            return TypedActionResult.fail(ItemStack.EMPTY);
        }

        player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.2f, 0.2f);

        return TypedActionResult.pass(ItemStack.EMPTY);
    }

    protected void win(ServerPlayerEntity winner) {
        var domain = manager.getDomains().get(winner.getUuid());

        domain.removeBlocks(sequence.getItems().getLast());

        //play victory animation
        Task task = new Task(winner, domain, sequence);
        TaskHandle taskHandle = gameHandle.getScheduler().interval(task, 5);

        winManager.win(winner).then(taskHandle::cancel);
    }

    private static class Task implements SchedulerAction {

        private final ServerPlayerEntity player;
        private final AimMasterDomain domain;
        private final AimMasterSequence sequence;
        int time = 0;

        private Task(ServerPlayerEntity player, AimMasterDomain domain, AimMasterSequence sequence) {
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
