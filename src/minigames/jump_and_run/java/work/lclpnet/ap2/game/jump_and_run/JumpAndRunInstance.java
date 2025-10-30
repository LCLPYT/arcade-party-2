package work.lclpnet.ap2.game.jump_and_run;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.scoreboard.*;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.core.hook.DripLeafTiltCallback;
import work.lclpnet.ap2.game.jump_and_run.gen.JumpAndRun;
import work.lclpnet.ap2.game.jump_and_run.gen.JumpAndRunSetup;
import work.lclpnet.ap2.game.jump_and_run.gen.JumpModule;
import work.lclpnet.ap2.impl.game.FFAGameInstance;
import work.lclpnet.ap2.impl.game.data.IntScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.util.SoundHelper;
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedPlayerBossBar;
import work.lclpnet.ap2.impl.util.checkpoint.CheckpointHelper;
import work.lclpnet.ap2.impl.util.checkpoint.CheckpointManager;
import work.lclpnet.ap2.impl.util.handler.Visibility;
import work.lclpnet.ap2.impl.util.handler.VisibilityHandler;
import work.lclpnet.ap2.impl.util.handler.VisibilityManager;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.gaco.collisions.ChunkedCollisionDetector;
import work.lclpnet.gaco.collisions.CollisionDetector;
import work.lclpnet.gaco.collisions.movement.PlayerMovementObserver;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.gaco.ds.Checkpoint;
import work.lclpnet.gaco.ds.PositionedBlockSet;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.util.PositionRotation;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskHandle;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static java.lang.Math.*;
import static net.minecraft.util.Formatting.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class JumpAndRunInstance extends FFAGameInstance implements MapBootstrap {

    private static final int
            ASSISTANCE_TICKS_BASE = Ticks.seconds(90),  // time after which assistance is provided
            REACH_GOAL_REQUIRED = 3,
            NEXT_PHASE_WAIT_TICKS = Ticks.seconds(4);

    private static final float
            TARGET_MINUTES = 4.0f;  // target completion time of the jump and run (approximate)

    private final IntScoreDataContainer<ServerPlayerEntity, PlayerRef> data = new IntScoreDataContainer<>(PlayerRef::create);
    private final CollisionDetector collisionDetector = new ChunkedCollisionDetector();
    private final PlayerMovementObserver movementObserver;
    private final List<BlockPos> gateBlocks = new ArrayList<>();
    private JumpAndRun jumpAndRun;
    private CheckpointManager checkpointManager;
    private DynamicTranslatedPlayerBossBar bossBar;
    private volatile boolean segmentActive = false;
    private final Set<UUID> inGoal = new HashSet<>();
    private @Nullable CompletableFuture<?> waitFor = null;
    private @Nullable TaskHandle task = null;

    public JumpAndRunInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        movementObserver = new PlayerMovementObserver(collisionDetector, gameHandle.getParticipants()::isParticipating);

        gameHandle.whenDone(() -> {
            var future = waitFor;

            if (future != null) {
                future.join();
            }
        });
    }

    @Override
    protected DataContainer<ServerPlayerEntity, PlayerRef> getData() {
        return data;
    }

    @Override
    public @NotNull CompletableFuture<Void> createWorldBootstrap(@NotNull ServerWorld world, @NotNull GameMap map) {
        world.setTimeOfDay(4000);

        var setup = new JumpAndRunSetup(gameHandle, map, world, TARGET_MINUTES);

        return setup.setup().thenAccept(jumpAndRun -> this.jumpAndRun = jumpAndRun);
    }

    @Override
    protected void prepare() {
        commons().gameRuleBuilder()
                .set(GameRules.RANDOM_TICK_SPEED, 0)
                .set(GameRules.DO_DAYLIGHT_CYCLE, false);

        movementObserver.init(gameHandle.getHooks(), gameHandle.getServer());

        bossBar = usePlayerDynamicTaskDisplay(styled(0, YELLOW), styled(jumpAndRun.modules().size(), YELLOW));
        bossBar.setPercent(0);

        initModule();

        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();

        initScoreBoard(scoreboardManager);
        initTeam(scoreboardManager);

        giveItemsToPlayers();

        jumpAndRun.setReloadModuleCallback(this::loadAndInitModule);
        new SetModuleCommand(jumpAndRun, gameHandle.getLogger()).register(gameHandle.getCommands());
    }

    @Override
    public void participantRemoved(ServerPlayerEntity player) {
        super.participantRemoved(player);

        if (!winManager.isGameOver()) {
            checkSegmentComplete();
        }
    }

    private void initScoreBoard(CustomScoreboardManager scoreboardManager) {
        ScoreboardObjective objective = scoreboardManager.createObjective("points", ScoreboardCriterion.DUMMY,
                Text.literal("Points").formatted(YELLOW, BOLD), ScoreboardCriterion.RenderType.INTEGER,
                StyledNumberFormat.YELLOW);

        useScoreboardStatsSync(data, objective);

        scoreboardManager.setDisplay(ScoreboardDisplaySlot.LIST, objective);
    }

    private void initTeam(CustomScoreboardManager scoreboardManager) {
        Team team = scoreboardManager.createTeam("team");
        team.setCollisionRule(AbstractTeam.CollisionRule.NEVER);
        scoreboardManager.joinTeam(gameHandle.getParticipants(), team);

        VisibilityHandler visibility = new VisibilityHandler(new VisibilityManager(team, Visibility.PARTIALLY_VISIBLE), gameHandle.getTranslations(), gameHandle.getParticipants());
        visibility.init(gameHandle.getHooks());
        visibility.giveItems();
    }

    @Override
    protected void go() {
        beginSegment();

        gameHandle.protect(config -> {
            config.allow(ProtectionTypes.USE_BLOCK, (entity, pos) -> {
                BlockState state = jumpAndRun.world().getBlockState(pos);
                return state.isOf(Blocks.SHULKER_BOX);
            });

            config.allow(ProtectionTypes.ALLOW_DAMAGE, (entity, source) -> {
                if (entity instanceof ServerPlayerEntity player
                        && gameHandle.getParticipants().isParticipating(player)
                        && (source.isOf(DamageTypes.IN_FIRE) || source.isOf(DamageTypes.LAVA)
                        || source.isOf(DamageTypes.HOT_FLOOR) || source.isOf(DamageTypes.OUT_OF_WORLD))) {

                    resetPlayerToCheckpoint(player);
                }
                return false;
            });
        });

        Participants participants = gameHandle.getParticipants();
        HookRegistrar hooks = gameHandle.getHooks();

        CheckpointHelper.setupResetItem(hooks, () -> winManager.isGameOver() || !segmentActive, participants::isParticipating)
                .then(this::resetPlayerToCheckpoint);

        CheckpointHelper.whenFallingIntoLava(hooks, participants::isParticipating)
                .then(this::resetPlayerToCheckpoint);

        commons().whenBelowY(() -> jumpAndRun.world().getBottomY())
                .then(this::resetPlayerToCheckpoint);

        // disable drip leaf tilt for players in goal
        hooks.registerHook(DripLeafTiltCallback.HOOK, (entity, pos) -> entity instanceof ServerPlayerEntity player
                && inGoal.contains(player.getUuid()));
    }

    private void giveItemsToPlayers() {
        CheckpointHelper.giveResetItem(gameHandle.getParticipants(), getWorld(), gameHandle.getTranslations(), 4);

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            PlayerInventoryAccess.setSelectedSlot(player, 4);
        }
    }

    private void openGate() {
        ServerWorld world = jumpAndRun.world();
        BlockState air = Blocks.AIR.getDefaultState();

        for (BlockPos pos : gateBlocks) {
            world.setBlockState(pos, air);
        }

        gateBlocks.clear();
    }

    private void closeGate() {
        gateBlocks.clear();

        List<BlockBox> gate = jumpAndRun.startGates();
        ServerWorld world = jumpAndRun.world();

        BlockState state = Blocks.WHITE_STAINED_GLASS.getDefaultState();

        for (BlockBox box : gate) {
            for (BlockPos pos : box) {
                if (!world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()) continue;

                world.setBlockState(pos, state);
                gateBlocks.add(pos.toImmutable());
            }
        }
    }

    private void resetPlayerToCheckpoint(ServerPlayerEntity player) {
        Checkpoint checkpoint = checkpointManager.getCheckpoint(player);

        Vec3d pos = checkpoint.pos();
        player.teleport(jumpAndRun.world(), pos.getX(), pos.getY(), pos.getZ(), Set.of(), checkpoint.yaw(), checkpoint.pitch(), true);

        player.setFireTicks(0);
    }

    private void delayAssistance() {
        var module = jumpAndRun.module();
        var schema = jumpAndRun.schema();

        PositionedBlockSet assistance = schema.getAssistance();

        if (assistance.blocks().isEmpty()) return;

        float weight = 1f + (module.data().estimatedMinutes() - 1f) * 0.5f;
        int timeout = max(ASSISTANCE_TICKS_BASE, round(ASSISTANCE_TICKS_BASE * weight));

        TaskHandle prevTask = task;

        if (prevTask != null) task.cancel();

        task = gameHandle.getScheduler().timeout(() -> placeAssistance(assistance), timeout);
    }

    private void placeAssistance(PositionedBlockSet assistance) {
        ServerWorld world = jumpAndRun.world();

        assistance.forEach(pb -> {
            BlockPos pos = pb.pos();

            world.setBlockState(pos, pb.state());
            world.spawnParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    5, 0.3, 0.3, 0.3, 0.1);
        });

        Translations translations = gameHandle.getTranslations();

        for (ServerPlayerEntity player : PlayerLookup.world(world)) {
            player.playSoundToPlayer(SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, 1f, 1.7f);

            var msg = translations.translateText(player, "game.ap2.jump_and_run.assistance")
                    .formatted(Formatting.GRAY);

            player.sendMessage(msg);
        }
    }

    private void initModule() {
        segmentActive = false;
        inGoal.clear();

        JumpModule module = jumpAndRun.module();

        if (module == null) return;

        closeGate();

        PositionRotation spawn = jumpAndRun.spawn();
        ServerWorld world = jumpAndRun.world();

        disableEffects();
        enableEffects(module.data().effects());

        for (ServerPlayerEntity player : PlayerLookup.all(gameHandle.getServer())) {
            player.teleport(world, spawn.getX(), spawn.getY(), spawn.getZ(), Set.of(), spawn.getYaw(), spawn.getPitch(), true);
        }

        collisionDetector.clear();
        movementObserver.clear();

        if (checkpointManager != null) {
            checkpointManager.destroy();
        }

        List<Checkpoint> checkpoints = jumpAndRun.checkpoints();
        checkpointManager = new CheckpointManager(checkpoints, commons().debugController());
        checkpointManager.init(collisionDetector, movementObserver, world);
        CheckpointHelper.notifyWhenReached(checkpointManager, gameHandle.getTranslations());

        movementObserver.whenEntering(jumpAndRun.endCheckpoint().bounds(), player -> {
            checkpointManager.grantCheckpoint(player, checkpoints.size() - 1);
            onReachedGoal(player, true);
        });

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            bossBar.setArgument(player, 0, styled(jumpAndRun.moduleIndex() + 1, YELLOW));
        }

        bossBar.setPercent((float) (jumpAndRun.moduleIndex()) / jumpAndRun.modules().size());
    }

    private void onReachedGoal(ServerPlayerEntity player, boolean reached) {
        if (requiredAmountReachedGoal() || !inGoal.add(player.getUuid())) return;

        data.addScore(player, max(0, REACH_GOAL_REQUIRED - inGoal.size() + 1));

        player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.5f, 2f);

        int room = jumpAndRun.moduleIndex() + 1;

        String key = reached ? "game.ap2.jump_and_run.completed_room" : "game.ap2.jump_and_run.last_not_completed";

        player.sendMessage(gameHandle.getTranslations().translateText(player, key, styled("#" + room, Formatting.YELLOW))
                .formatted(Formatting.GREEN));

        bossBar.setArgument(player, 0, styled(room, YELLOW));

        int segments = jumpAndRun.modules().size();

        if (segments > 0) {
            bossBar.getBossBar(player).setPercent((float) (room) / segments);
        }

        if (!requiredAmountReachedGoal()) {
            Participants participants = gameHandle.getParticipants();
            int notYetInGoal = participants.count() - inGoal.size();

            if (notYetInGoal == 1) {
                var lastRemaining = participants.stream()
                        .filter(p -> !inGoal.contains(p.getUuid()))
                        .findFirst();

                if (lastRemaining.isPresent()) {
                    onReachedGoal(lastRemaining.get(), false);
                    return;
                }
            }
        }

        checkSegmentComplete();
    }

    private void checkSegmentComplete() {
        if (!requiredAmountReachedGoal()) return;

        cancelPreviousTask();

        jumpAndRun.onModuleCompleted();

        if (jumpAndRun.isDone()) {
            winManager.complete();
            return;
        }

        ServerWorld world = jumpAndRun.world();

        gameHandle.getTranslations().translateText("game.ap2.jump_and_run.next_segment_wait").formatted(GRAY)
                .sendTo(PlayerLookup.world(world));

        SoundHelper.playSound(world, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.5f, 2f);

        loadAndInitModule();
    }

    private void loadAndInitModule() {
        var prevFuture = waitFor;

        if (prevFuture != null) {
            prevFuture.join();
            waitFor = null;
        }

        cancelPreviousTask();

        jumpAndRun.loadModule().thenRun(() -> gameHandle.getServer().execute(() -> {
            initModule();

            cancelPreviousTask();
            task = gameHandle.getScheduler().timeout(this::nextSegment, NEXT_PHASE_WAIT_TICKS);

            waitFor = jumpAndRun.unloadPreviousModule().whenComplete((res, err) -> waitFor = null);
        })).whenComplete((_res, err) -> {
            if (err != null) {
                gameHandle.getLogger().error("Failed to load module", err);
            }
        });
    }

    private void cancelPreviousTask() {
        TaskHandle task = this.task;

        if (task != null) {
            task.cancel();
        }
    }

    private boolean requiredAmountReachedGoal() {
        return inGoal.size() >= requiredAmount();
    }

    private int requiredAmount() {
        return min(gameHandle.getParticipants().count(), REACH_GOAL_REQUIRED);
    }

    private void nextSegment() {
        gameHandle.getTranslations().translateText("ap2.go").formatted(RED).acceptEach(PlayerLookup.world(jumpAndRun.world()), (player, text) -> {
            Title.get(player).title(text, Text.empty(), 5, 20, 5);
            player.playSoundToPlayer(SoundEvents.ENTITY_CHICKEN_EGG, SoundCategory.PLAYERS, 1, 0);
        });

        beginSegment();
    }

    private void beginSegment() {
        openGate();
        segmentActive = true;
        delayAssistance();
    }
}
