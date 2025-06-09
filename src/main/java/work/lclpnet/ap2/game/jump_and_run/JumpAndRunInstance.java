package work.lclpnet.ap2.game.jump_and_run;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.scoreboard.*;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.api.util.CollisionDetector;
import work.lclpnet.ap2.api.util.heads.PlayerHead;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.ap2.base.resource.ApResources;
import work.lclpnet.ap2.game.jump_and_run.gen.*;
import work.lclpnet.ap2.impl.game.FFAGameInstance;
import work.lclpnet.ap2.impl.game.data.ScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.util.ApRegistries;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.SoundHelper;
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedPlayerBossBar;
import work.lclpnet.ap2.impl.util.checkpoint.Checkpoint;
import work.lclpnet.ap2.impl.util.checkpoint.CheckpointHelper;
import work.lclpnet.ap2.impl.util.checkpoint.CheckpointManager;
import work.lclpnet.ap2.impl.util.collision.ChunkedCollisionDetector;
import work.lclpnet.ap2.impl.util.collision.PlayerMovementObserver;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.ap2.impl.util.handler.Visibility;
import work.lclpnet.ap2.impl.util.handler.VisibilityHandler;
import work.lclpnet.ap2.impl.util.handler.VisibilityManager;
import work.lclpnet.ap2.impl.util.heads.PlayerHeads;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static net.minecraft.util.Formatting.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class JumpAndRunInstance extends FFAGameInstance implements MapBootstrap {

    private static final int
            ASSISTANCE_TICKS_BASE = Ticks.seconds(90),  // time after which assistance is provided
            REACH_GOAL_REQUIRED = 3,
            NEXT_PHASE_WAIT_TICKS = Ticks.seconds(4);

    private static final float
            TARGET_MINUTES = 4.0f;  // target completion time of the jump and run (approximate)

    private static final boolean DEBUG_ASSISTANCE = true;

    private final ScoreDataContainer<ServerPlayerEntity, PlayerRef> data = new ScoreDataContainer<>(PlayerRef::create);
    private final CollisionDetector collisionDetector = new ChunkedCollisionDetector();
    private final PlayerMovementObserver movementObserver;
    private final List<BlockPos> gateBlocks = new ArrayList<>();
    private final DebugController debugController = new DebugController();
    private JumpAndRun jumpAndRun;
    private CheckpointManager checkpoints;
    private DynamicTranslatedPlayerBossBar bossBar;
    private volatile int segmentIndex = 0;
    private volatile boolean segmentActive = false;
    private final Set<UUID> inGoal = new HashSet<>();

    public JumpAndRunInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
        movementObserver = new PlayerMovementObserver(collisionDetector, gameHandle.getParticipants()::isParticipating);
    }

    @Override
    protected DataContainer<ServerPlayerEntity, PlayerRef> getData() {
        return data;
    }

    @Override
    public CompletableFuture<Void> createWorldBootstrap(ServerWorld world, GameMap map) {
        world.setTimeOfDay(4000);

        var setup = new JumpAndRunSetup(gameHandle, map, world, TARGET_MINUTES);

        return setup.setup().thenAccept(jumpAndRun -> this.jumpAndRun = jumpAndRun);
    }

    @Override
    protected void prepare() {
        commons().gameRuleBuilder()
                .set(GameRules.RANDOM_TICK_SPEED, 0)
                .set(GameRules.DO_DAYLIGHT_CYCLE, false);

        movementObserver.init(gameHandle.getHookRegistrar(), gameHandle.getServer());

        bossBar = usePlayerDynamicTaskDisplay(styled(0, YELLOW), styled(jumpAndRun.segments().size(), YELLOW));
        bossBar.setPercent(0);

        setSegment(0);

        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();

        initScoreBoard(scoreboardManager);
        initTeam(scoreboardManager);

        giveItemsToPlayers();
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
        visibility.init(gameHandle.getHookRegistrar());
        visibility.giveItems();
    }

    @Override
    protected void ready() {
        beginSegment();

        gameHandle.protect(config -> {
            config.allow(ProtectionTypes.USE_BLOCK, (entity, pos) -> {
                BlockState state = entity.getWorld().getBlockState(pos);
                return state.isOf(Blocks.SHULKER_BOX);
            });

            config.allow(ProtectionTypes.ALLOW_DAMAGE, (entity, source) -> {
                if (entity instanceof ServerPlayerEntity player
                        && gameHandle.getParticipants().isParticipating(player)
                        && source.isIn(DamageTypeTags.IS_FIRE)) {

                    resetPlayerToCheckpoint(player);
                }
                return false;
            });
        });

        Participants participants = gameHandle.getParticipants();
        HookRegistrar hooks = gameHandle.getHookRegistrar();

        CheckpointHelper.setupResetItem(hooks, () -> winManager.isGameOver() || !segmentActive, participants::isParticipating)
                .then(this::resetPlayerToCheckpoint);

        CheckpointHelper.whenFallingIntoLava(hooks, participants::isParticipating)
                .then(this::resetPlayerToCheckpoint);
    }

    private void giveItemsToPlayers() {
        Translations translations = gameHandle.getTranslations();
        PlayerHead head = getWorld().getRegistryManager()
                .getOrThrow(ApRegistries.PLAYER_HEAD)
                .getOptionalValue(PlayerHeads.REDSTONE_BLOCK_REFRESH)
                .orElseThrow();

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            ItemStack stack = head.createStack();

            var name = translations.translateText(player, "ap2.game.reset").formatted(Formatting.RED);
            stack.set(DataComponentTypes.CUSTOM_NAME, name.styled(style -> style.withItalic(false)));

            player.getInventory().setStack(4, stack);
            PlayerInventoryAccess.setSelectedSlot(player, 4);
        }
    }

    private void openGate() {
        ServerWorld world = getWorld();
        BlockState air = Blocks.AIR.getDefaultState();

        for (BlockPos pos : gateBlocks) {
            world.setBlockState(pos, air);
        }

        gateBlocks.clear();
    }

    private void closeGate() {
        var segments = jumpAndRun.segments();

        if (segmentIndex < 0 || segmentIndex >= segments.size()) return;

        gateBlocks.clear();

        BlockBox gate = segments.get(segmentIndex).gate();
        ServerWorld world = getWorld();

        BlockState state = Blocks.WHITE_STAINED_GLASS.getDefaultState();

        for (BlockPos pos : gate) {
            if (!world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()) continue;

            world.setBlockState(pos, state);
            gateBlocks.add(pos.toImmutable());
        }
    }

    private void resetPlayerToCheckpoint(ServerPlayerEntity player) {
        Checkpoint checkpoint = checkpoints.getCheckpoint(player);

        BlockPos pos = checkpoint.pos();
        player.teleport(getWorld(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), checkpoint.yaw(), 0f, true);

        player.setFireTicks(0);
    }

    private void delayAssistance() {
        var segments = jumpAndRun.segments();

        if (segmentIndex < 0 || segmentIndex >= segments.size()) return;

        Segment segment = segments.get(segmentIndex);

        RoomData data = segment.roomInfo().data();

        if (data == null || data.assistance().blocks().isEmpty()) return;

        float weight = 1f + (data.value() - 1f) * 0.5f;
        int timeout = Math.max(ASSISTANCE_TICKS_BASE, Math.round(ASSISTANCE_TICKS_BASE * weight));

        gameHandle.getGameScheduler().timeout(() -> placeAssistance(segment.roomInfo()), timeout);
    }

    private void placeAssistance(RoomInfo room) {
        RoomData data = room.data();

        if (data == null) return;

        ServerWorld world = getWorld();

        data.assistance().forEach((state, pos) -> {
            world.setBlockState(pos, state);
            world.spawnParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    5, 0.3, 0.3, 0.3, 0.1);
        });

        BlockBox bounds = room.bounds();
        Translations translations = gameHandle.getTranslations();

        for (ServerPlayerEntity player : PlayerLookup.all(gameHandle.getServer())) {
            if (!bounds.contains(player.getX(), player.getY(), player.getZ())) continue;

            player.playSoundToPlayer(SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, 1f, 1.7f);

            var msg = translations.translateText(player, "game.ap2.jump_and_run.assistance")
                    .formatted(Formatting.GRAY);

            player.sendMessage(msg);
        }
    }

    private void setSegment(int i) {
        var segments = jumpAndRun.segments();
        int segmentCount = segments.size();

        if (i < 0 || i >= segmentCount) return;

        segmentIndex = i;
        segmentActive = false;
        inGoal.clear();

        closeGate();

        Segment segment = segments.get(i);
        BlockPos spawn = segment.spawn();
        ServerWorld world = getWorld();

        for (ServerPlayerEntity player : PlayerLookup.world(world)) {
            player.teleport(world, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, Set.of(), segment.spawnYaw(), 0f, true);
        }

        collisionDetector.clear();
        movementObserver.clear();

        if (checkpoints != null) {
            checkpoints.destroy();
        }

        checkpoints = new CheckpointManager(segment.checkpoints());
        checkpoints.init(collisionDetector, movementObserver, world);
        CheckpointHelper.notifyWhenReached(checkpoints, gameHandle.getTranslations());

        movementObserver.whenEntering(segment.goalBounds(), player -> onReachedGoal(player, true));

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            bossBar.setArgument(player, 0, styled(segmentIndex, YELLOW));
        }

        bossBar.setPercent((float) (segmentIndex) / segmentCount);

        debugAssistance();
    }

    private void debugAssistance() {
        if (!ApConstants.DEBUG || !DEBUG_ASSISTANCE) return;

        debugController.destroy();
        debugController.init(ApResources.getInstance(), getWorld());

        List<Segment> segments = jumpAndRun.segments();

        if (segmentIndex < 0 || segmentIndex >= segments.size()) return;

        Segment segment = segments.get(segmentIndex);

        RoomData data = segment.roomInfo().data();

        if (data == null) return;

        debugController.renderer().ifPresent(renderer -> data.assistance().forEach((state, pos) ->
                renderer.marker(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, state, 0, 0.5)));
    }

    private void onReachedGoal(ServerPlayerEntity player, boolean reached) {
        if (requiredAmountReachedGoal() || !inGoal.add(player.getUuid())) return;

        data.addScore(player, max(0, REACH_GOAL_REQUIRED - inGoal.size() + 1));

        player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.5f, 2f);

        int room = segmentIndex + 1;

        String key = reached ? "game.ap2.jump_and_run.completed_room" : "game.ap2.jump_and_run.last_not_completed";

        player.sendMessage(gameHandle.getTranslations().translateText(player, key, styled("#" + room, Formatting.YELLOW))
                .formatted(Formatting.GREEN));

        bossBar.setArgument(player, 0, styled(room, YELLOW));

        int segments = jumpAndRun.segments().size();

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

        int nextSegment = segmentIndex + 1;

        if (nextSegment >= jumpAndRun.segments().size()) {
            winManager.complete();
            return;
        }

        gameHandle.getTranslations().translateText("game.ap2.jump_and_run.next_segment_wait").formatted(GRAY).sendTo(PlayerLookup.world(getWorld()));

        setSegment(nextSegment);

        SoundHelper.playSound(getWorld(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.5f, 2f);

        gameHandle.getGameScheduler().timeout(this::nextSegment, NEXT_PHASE_WAIT_TICKS);
    }

    private boolean requiredAmountReachedGoal() {
        return inGoal.size() >= requiredAmount();
    }

    private int requiredAmount() {
        return min(gameHandle.getParticipants().count(), REACH_GOAL_REQUIRED);
    }

    private void nextSegment() {
        gameHandle.getTranslations().translateText("ap2.go").formatted(RED).acceptEach(PlayerLookup.world(getWorld()), (player, text) -> {
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
