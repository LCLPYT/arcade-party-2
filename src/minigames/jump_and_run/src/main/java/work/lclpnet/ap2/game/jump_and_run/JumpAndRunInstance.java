package work.lclpnet.ap2.game.jump_and_run;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.core.hook.DripLeafTiltCallback;
import work.lclpnet.ap2.game.MiniGameHandle;
import work.lclpnet.ap2.game.base.FFAGameInstance;
import work.lclpnet.ap2.game.jump_and_run.gen.JumpAndRun;
import work.lclpnet.ap2.game.jump_and_run.gen.JumpModule;
import work.lclpnet.ap2.game.player.Participants;
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
import work.lclpnet.game.impl.prot.ProtectionTypes;
import work.lclpnet.game.map.GameMap;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.access.entity.ServerPlayerAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.util.PositionRotation;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskHandle;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.Translations;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static java.lang.Math.*;
import static net.minecraft.ChatFormatting.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class JumpAndRunInstance extends FFAGameInstance {

    private static final int
            ASSISTANCE_TICKS_BASE = Ticks.seconds(90),  // time after which assistance is provided
            REACH_GOAL_REQUIRED = 3,
            NEXT_PHASE_WAIT_TICKS = Ticks.seconds(4);

    public static final float TARGET_MINUTES = 4.0f;  // target completion time of the jump and run (approximate)

    private final IntScoreDataContainer<ServerPlayer, PlayerRef> data = new IntScoreDataContainer<>(PlayerRef::create);
    private final CollisionDetector collisionDetector = new ChunkedCollisionDetector();
    private final PlayerMovementObserver movementObserver;
    private final List<BlockPos> gateBlocks = new ArrayList<>();
    private final JumpAndRun jumpAndRun;
    private CheckpointManager checkpointManager;
    private DynamicTranslatedPlayerBossBar bossBar;
    private volatile boolean segmentActive = false;
    private final Set<UUID> inGoal = new HashSet<>();
    private @Nullable CompletableFuture<?> waitFor = null;
    private @Nullable TaskHandle task = null;

    public JumpAndRunInstance(MiniGameHandle gameHandle, ServerLevel world, GameMap map, JumpAndRun jumpAndRun) {
        super(gameHandle, world, map);

        this.jumpAndRun = jumpAndRun;

        movementObserver = new PlayerMovementObserver(collisionDetector, getGameHandle().getParticipants()::isParticipating);

        getGameHandle().whenDone(() -> {
            var future = waitFor;

            if (future != null) {
                future.join();
            }
        });
    }

    @Override
    protected @NonNull DataContainer<ServerPlayer, PlayerRef> getData() {
        return data;
    }

    @Override
    protected void prepare() {
        commons().gameRuleBuilder()
                .set(GameRules.RANDOM_TICK_SPEED, 0)
                .set(GameRules.ADVANCE_TIME, false);

        movementObserver.init(getGameHandle().getHooks(), getGameHandle().getServer());

        bossBar = usePlayerDynamicTaskDisplay(styled(0, YELLOW), styled(jumpAndRun.modules().size(), YELLOW));
        bossBar.setPercent(0);

        initModule();

        CustomScoreboardManager scoreboardManager = getGameHandle().getScoreboardManager();

        initScoreBoard(scoreboardManager);
        initTeam(scoreboardManager);

        giveItemsToPlayers();

        jumpAndRun.setReloadModuleCallback(this::loadAndInitModule);
        new SetModuleCommand(jumpAndRun, getGameHandle().getLogger()).register(getGameHandle().getCommands());
    }

    @Override
    public void participantRemoved(@NonNull ServerPlayer player) {
        super.participantRemoved(player);

        if (!winManager.isGameOver()) {
            checkSegmentComplete();
        }
    }

    private void initScoreBoard(CustomScoreboardManager scoreboardManager) {
        Objective objective = scoreboardManager.createObjective("points", ObjectiveCriteria.DUMMY,
                Component.literal("Points").withStyle(YELLOW, BOLD), ObjectiveCriteria.RenderType.INTEGER,
                StyledFormat.PLAYER_LIST_DEFAULT);

        useScoreboardStatsSync(data, objective);

        scoreboardManager.setDisplay(DisplaySlot.LIST, objective);
    }

    private void initTeam(CustomScoreboardManager scoreboardManager) {
        PlayerTeam team = scoreboardManager.createTeam("team");
        team.setCollisionRule(Team.CollisionRule.NEVER);
        scoreboardManager.joinTeam(getGameHandle().getParticipants(), team);

        VisibilityHandler visibility = new VisibilityHandler(new VisibilityManager(team, Visibility.PARTIALLY_VISIBLE), getGameHandle().getTranslations(), getGameHandle().getParticipants());
        visibility.init(getGameHandle().getHooks());
        visibility.giveItems();
    }

    @Override
    protected void go() {
        beginSegment();

        getGameHandle().protect(config -> {
            ProtectionTypes.USE_BLOCK.allow(config, (_, pos) -> {
                BlockState state = jumpAndRun.world().getBlockState(pos);
                return state.is(Blocks.SHULKER_BOX);
            });

            ProtectionTypes.ALLOW_DAMAGE.allow(config, (entity, source) -> {
                if (entity instanceof ServerPlayer player
                        && getGameHandle().getParticipants().isParticipating(player)
                        && (source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.LAVA)
                        || source.is(DamageTypes.HOT_FLOOR) || source.is(DamageTypes.FELL_OUT_OF_WORLD))) {

                    resetPlayerToCheckpoint(player);
                }
                return false;
            });
        });

        Participants participants = getGameHandle().getParticipants();
        HookRegistrar hooks = getGameHandle().getHooks();

        CheckpointHelper.setupResetItem(hooks, () -> winManager.isGameOver() || !segmentActive, participants::isParticipating)
                .then(this::resetPlayerToCheckpoint);

        CheckpointHelper.whenFallingIntoLava(hooks, participants::isParticipating)
                .then(this::resetPlayerToCheckpoint);

        commons().whenBelowY(() -> jumpAndRun.world().getMinY())
                .then(this::resetPlayerToCheckpoint);

        // disable drip leaf tilt for players in goal
        DripLeafTiltCallback.HOOK.registerWith(hooks, (entity, _) -> entity instanceof ServerPlayer player
                && inGoal.contains(player.getUUID()));
    }

    private void giveItemsToPlayers() {
        CheckpointHelper.giveResetItem(getGameHandle().getParticipants(), getLevel(), getGameHandle().getTranslations(), 4);

        for (ServerPlayer player : getGameHandle().getParticipants()) {
            PlayerInventoryAccess.setSelectedSlot(player, 4);
        }
    }

    private void openGate() {
        ServerLevel world = jumpAndRun.world();
        BlockState air = Blocks.AIR.defaultBlockState();

        for (BlockPos pos : gateBlocks) {
            world.setBlockAndUpdate(pos, air);
        }

        gateBlocks.clear();
    }

    private void closeGate() {
        gateBlocks.clear();

        List<BlockBox> gate = jumpAndRun.startGates();
        ServerLevel world = jumpAndRun.world();

        BlockState state = Blocks.WHITE_STAINED_GLASS.defaultBlockState();

        for (BlockBox box : gate) {
            for (BlockPos pos : box) {
                if (!world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()) continue;

                world.setBlockAndUpdate(pos, state);
                gateBlocks.add(pos.immutable());
            }
        }
    }

    private void resetPlayerToCheckpoint(ServerPlayer player) {
        Checkpoint checkpoint = checkpointManager.getCheckpoint(player);

        Vec3 pos = checkpoint.pos();
        player.teleportTo(jumpAndRun.world(), pos.x(), pos.y(), pos.z(), Set.of(), checkpoint.yaw(), checkpoint.pitch(), true);

        player.setRemainingFireTicks(0);
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

        task = getGameHandle().getScheduler().timeout(() -> placeAssistance(assistance), timeout);
    }

    private void placeAssistance(PositionedBlockSet assistance) {
        ServerLevel world = jumpAndRun.world();

        assistance.forEach(pb -> {
            BlockPos pos = pb.pos();

            world.setBlockAndUpdate(pos, pb.state());
            world.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    5, 0.3, 0.3, 0.3, 0.1);
        });

        Translations translations = getGameHandle().getTranslations();

        for (ServerPlayer player : PlayerLookup.level(world)) {
            ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1f, 1.7f);

            var msg = translations.translateText(player, "game.ap2.jump_and_run.assistance")
                    .formatted(ChatFormatting.GRAY);

            player.sendSystemMessage(msg);
        }
    }

    private void initModule() {
        segmentActive = false;
        inGoal.clear();

        JumpModule module = jumpAndRun.module();

        if (module == null) return;

        closeGate();

        PositionRotation spawn = jumpAndRun.spawn();
        ServerLevel world = jumpAndRun.world();

        disableEffects();
        enableEffects(module.data().effects());

        for (ServerPlayer player : PlayerLookup.all(getGameHandle().getServer())) {
            player.teleportTo(world, spawn.x(), spawn.y(), spawn.z(), Set.of(), spawn.getYaw(), spawn.getPitch(), true);
        }

        collisionDetector.clear();
        movementObserver.clear();

        if (checkpointManager != null) {
            checkpointManager.destroy();
        }

        List<Checkpoint> checkpoints = jumpAndRun.checkpoints();
        checkpointManager = new CheckpointManager(checkpoints, commons().debugController());
        checkpointManager.init(collisionDetector, movementObserver, world);
        CheckpointHelper.notifyWhenReached(checkpointManager, getGameHandle().getTranslations());

        movementObserver.whenEntering(jumpAndRun.endCheckpoint().bounds(), player -> {
            checkpointManager.grantCheckpoint(player, checkpoints.size() - 1);
            onReachedGoal(player, true);
        });

        for (ServerPlayer player : getGameHandle().getParticipants()) {
            bossBar.setArgument(player, 0, styled(jumpAndRun.moduleIndex() + 1, YELLOW));
        }

        bossBar.setPercent((float) (jumpAndRun.moduleIndex()) / jumpAndRun.modules().size());
    }

    private void onReachedGoal(ServerPlayer player, boolean reached) {
        if (requiredAmountReachedGoal() || !inGoal.add(player.getUUID())) return;

        data.addScore(player, max(0, REACH_GOAL_REQUIRED - inGoal.size() + 1));

        ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 2f);

        int room = jumpAndRun.moduleIndex() + 1;

        String key = reached ? "game.ap2.jump_and_run.completed_room" : "game.ap2.jump_and_run.last_not_completed";

        player.sendSystemMessage(getGameHandle().getTranslations().translateText(player, key, styled("#" + room, ChatFormatting.YELLOW))
                .formatted(ChatFormatting.GREEN));

        bossBar.setArgument(player, 0, styled(room, YELLOW));

        int segments = jumpAndRun.modules().size();

        if (segments > 0) {
            bossBar.getBossBar(player).setProgress((float) (room) / segments);
        }

        if (!requiredAmountReachedGoal()) {
            Participants participants = getGameHandle().getParticipants();
            int notYetInGoal = participants.count() - inGoal.size();

            if (notYetInGoal == 1) {
                var lastRemaining = participants.stream()
                        .filter(p -> !inGoal.contains(p.getUUID()))
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

        ServerLevel world = jumpAndRun.world();

        getGameHandle().getTranslations().translateText("game.ap2.jump_and_run.next_segment_wait").formatted(GRAY)
                .sendTo(PlayerLookup.level(world));

        SoundHelper.playSound(world, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 2f);

        loadAndInitModule();
    }

    private void loadAndInitModule() {
        var prevFuture = waitFor;

        if (prevFuture != null) {
            prevFuture.join();
            waitFor = null;
        }

        cancelPreviousTask();

        jumpAndRun.loadModule().thenRun(() -> getGameHandle().getServer().execute(() -> {
            initModule();

            cancelPreviousTask();
            task = getGameHandle().getScheduler().timeout(this::nextSegment, NEXT_PHASE_WAIT_TICKS);

            waitFor = jumpAndRun.unloadPreviousModule().whenComplete((_, _) -> waitFor = null);
        })).whenComplete((_, err) -> {
            if (err != null) {
                getGameHandle().getLogger().error("Failed to load module", err);
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
        return min(getGameHandle().getParticipants().count(), REACH_GOAL_REQUIRED);
    }

    private void nextSegment() {
        getGameHandle().getTranslations().translateText("ap2.go").formatted(RED).acceptEach(PlayerLookup.level(jumpAndRun.world()), (player, text) -> {
            Title.get(player).title(text, Component.empty(), 5, 20, 5);
            ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.CHICKEN_EGG, SoundSource.PLAYERS, 1, 0);
        });

        beginSegment();
    }

    private void beginSegment() {
        openGate();
        segmentActive = true;
        delayAssistance();
    }
}
