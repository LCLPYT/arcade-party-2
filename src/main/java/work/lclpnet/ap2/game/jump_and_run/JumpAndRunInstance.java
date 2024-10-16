package work.lclpnet.ap2.game.jump_and_run;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameRules;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.api.util.CollisionDetector;
import work.lclpnet.ap2.game.jump_and_run.gen.*;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.ap2.impl.game.data.ScoreTimeDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedPlayerBossBar;
import work.lclpnet.ap2.impl.util.checkpoint.Checkpoint;
import work.lclpnet.ap2.impl.util.checkpoint.CheckpointHelper;
import work.lclpnet.ap2.impl.util.checkpoint.CheckpointManager;
import work.lclpnet.ap2.impl.util.collision.ChunkedCollisionDetector;
import work.lclpnet.ap2.impl.util.collision.PlayerMovementObserver;
import work.lclpnet.ap2.impl.util.handler.VisibilityHandler;
import work.lclpnet.ap2.impl.util.handler.VisibilityManager;
import work.lclpnet.ap2.impl.util.heads.PlayerHeadUtil;
import work.lclpnet.ap2.impl.util.heads.PlayerHeads;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.util.Formatting.BOLD;
import static net.minecraft.util.Formatting.YELLOW;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class JumpAndRunInstance extends DefaultGameInstance implements MapBootstrap {

    private static final int ASSISTANCE_TICKS_BASE = Ticks.seconds(90);  // time after which assistance is provided
    private static final float TARGET_MINUTES = 4.0f;  // target completion time of the jump and run (approximate)
    private final ScoreTimeDataContainer<ServerPlayerEntity, PlayerRef> data = new ScoreTimeDataContainer<>(PlayerRef::create);
    private final CollisionDetector collisionDetector = new ChunkedCollisionDetector();
    private final PlayerMovementObserver movementObserver;
    private final List<BlockPos> gateBlocks = new ArrayList<>();
    private JumpAndRun jumpAndRun;
    private CheckpointManager checkpoints;
    private DynamicTranslatedPlayerBossBar bossBar;
    private int reachedRoom = 0;

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

        JumpAndRunSetup setup = new JumpAndRunSetup(gameHandle, map, world, TARGET_MINUTES);

        return setup.setup().thenAccept(jumpAndRun -> this.jumpAndRun = jumpAndRun);
    }

    @Override
    protected void prepare() {
        commons().gameRuleBuilder()
                .set(GameRules.RANDOM_TICK_SPEED, 0)
                .set(GameRules.DO_DAYLIGHT_CYCLE, false);

        // setup room listeners
        var rooms = jumpAndRun.rooms();

        for (int i = 0, roomsSize = rooms.size(); i < roomsSize; i++) {
            BlockBox room = rooms.get(i).bounds();

            collisionDetector.add(room);

            int roomIndex = i;

            movementObserver.whenEntering(room, player -> {
                if (winManager.isGameOver()) return;

                enterRoom(player, roomIndex);
            });
        }

        movementObserver.init(gameHandle.getHookRegistrar(), gameHandle.getServer());

        // close gate until ready
        closeGate();

        // add scoreboard
        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();
        ScoreboardObjective objective = scoreboardManager.createObjective("points", ScoreboardCriterion.DUMMY,
                Text.literal("Points").formatted(YELLOW, BOLD), ScoreboardCriterion.RenderType.INTEGER,
                StyledNumberFormat.YELLOW);

        useScoreboardStatsSync(data, objective);

        scoreboardManager.setDisplay(ScoreboardDisplaySlot.LIST, objective);

        // add team to disable collision
        Team team = scoreboardManager.createTeam("team");
        team.setCollisionRule(AbstractTeam.CollisionRule.NEVER);
        scoreboardManager.joinTeam(gameHandle.getParticipants(), team);

        Translations translations = gameHandle.getTranslations();
        VisibilityHandler visibility = new VisibilityHandler(new VisibilityManager(team), translations, gameHandle.getParticipants());
        visibility.init(gameHandle.getHookRegistrar());

        checkpoints = new CheckpointManager(jumpAndRun.checkpoints());
        checkpoints.init(collisionDetector, movementObserver);
        checkpoints.whenCheckpointReached(this::onCheckpointReached);
        CheckpointHelper.notifyWhenReached(checkpoints, translations);

        bossBar = usePlayerDynamicTaskDisplay(styled(1, YELLOW), styled(jumpAndRun.rooms().size() - 2, YELLOW));
        bossBar.setPercent(0);

        giveItemsToPlayers();
        visibility.giveItems();
    }

    @Override
    protected void ready() {
        openGate();

        gameHandle.protect(config -> config.allow(ProtectionTypes.USE_BLOCK, (entity, pos) -> {
            BlockState state = entity.getWorld().getBlockState(pos);
            return state.isOf(Blocks.SHULKER_BOX);
        }));

        Participants participants = gameHandle.getParticipants();
        HookRegistrar hooks = gameHandle.getHookRegistrar();

        CheckpointHelper.setupResetItem(hooks, winManager::isGameOver, participants::isParticipating)
                .then(this::resetPlayerToCheckpoint);

        CheckpointHelper.whenFallingIntoLava(hooks, participants::isParticipating)
                .then(this::resetPlayerToCheckpoint);
    }

    private void giveItemsToPlayers() {
        Translations translations = gameHandle.getTranslations();

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            ItemStack stack = PlayerHeadUtil.getItem(PlayerHeads.REDSTONE_BLOCK_REFRESH);

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
    }

    private void closeGate() {
        BlockBox gate = jumpAndRun.gate();
        ServerWorld world = getWorld();

        BlockState state = Blocks.WHITE_STAINED_GLASS.getDefaultState();

        for (BlockPos pos : gate) {
            if (!world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()) continue;

            world.setBlockState(pos, state);
            gateBlocks.add(pos.toImmutable());
        }
    }

    private void enterRoom(ServerPlayerEntity player, int room) {
        delayAssistance(room);

        if (room <= data.getScore(player)) return;

        data.setScore(player, room);

        int checkpointOffset = jumpAndRun.getCheckpointOffset(room);
        checkpoints.grantCheckpoint(player, checkpointOffset);

        if (room <= 1) return;

        player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.5f, 2f);

        var msg = gameHandle.getTranslations().translateText(player, "game.ap2.jump_and_run.reached_room",
                        styled("#" + room, Formatting.YELLOW))
                .formatted(Formatting.GREEN);

        player.sendMessage(msg);

        int maxRooms = jumpAndRun.rooms().size() - 2;
        int canonicalRoom = MathHelper.clamp(room, 1, maxRooms);

        bossBar.setArgument(player, 0, styled(canonicalRoom, YELLOW));

        if (maxRooms > 0) {
            bossBar.getBossBar(player).setPercent((float) (room - 1) / maxRooms);
        }

        if (winManager.isGameOver() || room < jumpAndRun.rooms().size() - 1) return;

        winManager.win(player);
    }

    private void onCheckpointReached(ServerPlayerEntity player, int checkpoint) {
        int room = jumpAndRun.getRoomOfCheckpoint(checkpoint);

        enterRoom(player, room);
    }

    private void resetPlayerToCheckpoint(ServerPlayerEntity player) {
        Checkpoint checkpoint = checkpoints.getCheckpoint(player);

        BlockPos pos = checkpoint.pos();
        player.teleport(getWorld(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, checkpoint.yaw(), 0f);

        player.setFireTicks(0);
    }

    private void delayAssistance(int room) {
        synchronized (this) {
            if (room <= reachedRoom) return;

            reachedRoom = room;
        }

        List<RoomInfo> rooms = jumpAndRun.rooms();
        if (room < 0 || room >= rooms.size()) return;

        RoomInfo info = rooms.get(room);
        if (info == null) return;

        RoomData data = info.data();
        if (data == null) return;

        if (data.assistance().blocks().isEmpty()) return;

        float weight = 1f + (data.value() - 1f) * 0.5f;
        int timeout = Math.max(ASSISTANCE_TICKS_BASE, Math.round(ASSISTANCE_TICKS_BASE * weight));
        gameHandle.getGameScheduler().timeout(() -> placeAssistance(info), timeout);
    }

    private void placeAssistance(RoomInfo room) {
        RoomData data = room.data();
        if (data == null) return;

        JumpAssistance assistance = data.assistance();
        ServerWorld world = getWorld();

        for (var block : assistance.blocks()) {
            BlockPos pos = block.left();
            BlockState state = block.right();

            world.setBlockState(pos, state);
            world.spawnParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    5, 0.3, 0.3, 0.3, 0.1);
        }

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
}
