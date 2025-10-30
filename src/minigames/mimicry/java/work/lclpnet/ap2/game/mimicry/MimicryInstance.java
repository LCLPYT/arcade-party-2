package work.lclpnet.ap2.game.mimicry;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.game.mimicry.data.MimicryManager;
import work.lclpnet.ap2.game.mimicry.data.MimicryRoom;
import work.lclpnet.ap2.game.mimicry.data.SequencePlayer;
import work.lclpnet.ap2.impl.game.FFAGameInstance;
import work.lclpnet.ap2.impl.game.PseudoElimination;
import work.lclpnet.ap2.impl.game.data.IntDataContainer;
import work.lclpnet.ap2.impl.game.data.IntScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.Ordering;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.world.StackedRoomGenerator;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.gaco.math.AffineIntMatrix;
import work.lclpnet.kibu.hook.ServerMessageHooks;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.mc.KibuBlockPos;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.util.BossBarTimer;

import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class MimicryInstance extends FFAGameInstance implements MapBootstrap {

    private static final int
            PREPARE_TICKS = 50,
            REPLAY_MIN_SECONDS = 8,
            REPLAY_SECONDS_PER_NOTE = 1,
            REPLAY_MAX_SECONDS = 30,
            NEXT_ROUND_DELAY_SECONDS = 4;

    private final IntDataContainer<ServerPlayerEntity, PlayerRef> dataContainer = new IntScoreDataContainer<>(PlayerRef::create, Ordering.DESCENDING, "game.ap2.mimicry.completed");
    private PseudoElimination pseudoElimination;

    private MimicryManager manager = null;
    private SequencePlayer sequencePlayer = null;
    private BossBarTimer timer = null;
    private int timerTransaction = 0;
    private Phase phase = Phase.IDLE;

    public MimicryInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    protected DataContainer<ServerPlayerEntity, PlayerRef> getData() {
        return dataContainer;
    }

    @Override
    public @NotNull CompletableFuture<Void> createWorldBootstrap(@NotNull ServerWorld world, @NotNull GameMap map) {
        BlockBox buttons = MapUtil.readBox(map.requireProperty("button-box"));

        var generator = new StackedRoomGenerator<>(world, map, StackedRoomGenerator.Coordinates.ABSOLUTE, (pos, spawn, yaw, structure) -> {
            KibuBlockPos origin = structure.getOrigin();

            BlockBox roomButtons = buttons.transform(AffineIntMatrix.makeTranslation(
                    pos.getX() - origin.getX(),
                    pos.getY() - origin.getY(),
                    pos.getZ() - origin.getZ()));

            return new MimicryRoom(pos, spawn, yaw, roomButtons);
        });

        return generator.generate(gameHandle.getParticipants())
                .thenAccept(result -> {
                    var rooms = result.rooms();
                    Random random = new Random();

                    manager = new MimicryManager(gameHandle, rooms, buttons, random, world, this::onCompleted);
                })
                .exceptionally(throwable -> {
                    gameHandle.getLogger().error("Failed to create rooms", throwable);
                    return null;
                });
    }

    @Override
    protected void prepare() {
        ServerWorld world = getWorld();

        pseudoElimination = new PseudoElimination(gameHandle, world);

        manager.eachParticipant((player, room) -> room.teleport(player, world));

        gameHandle.getHooks().registerHook(ServerMessageHooks.ALLOW_CHAT_MESSAGE, (message, sender, params) -> false);
        gameHandle.getHooks().registerHook(ServerMessageHooks.ALLOW_COMMAND_MESSAGE, (message, sender, params) -> false);
    }

    @Override
    protected void go() {
        sequencePlayer = new SequencePlayer(manager, gameHandle.getGameScheduler(), getWorld());

        nextSequence();

        gameHandle.getHooks().registerHook(PlayerInteractionHooks.USE_BLOCK, this::onUseBlock);
    }

    private ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
        if (winManager.isGameOver()
                || !(player instanceof ServerPlayerEntity serverPlayer)
                || !gameHandle.getParticipants().isParticipating(serverPlayer)
                || pseudoElimination.isEliminated(serverPlayer)) {
            return ActionResult.PASS;
        }

        BlockPos pos = hitResult.getBlockPos();

        if (!world.getBlockState(pos).isIn(BlockTags.BUTTONS)) {
            return ActionResult.PASS;
        }

        if (!manager.onInputButton(serverPlayer, pos)) {
            return ActionResult.FAIL;
        }

        var msg = gameHandle.getTranslations().translateText(serverPlayer, "game.ap2.mimicry.wrong_button")
                .formatted(Formatting.RED);

        serverPlayer.sendMessage(msg);

        softEliminate(serverPlayer);

        return ActionResult.FAIL;
    }

    private synchronized void nextSequence() {
        if (phase != Phase.IDLE || winManager.isGameOver()) return;

        removeTimer();

        commons().announcer().announceSubtitle("game.ap2.mimicry.attention");

        gameHandle.getGameScheduler().timeout(this::playSequence, PREPARE_TICKS);
    }

    private synchronized void removeTimer() {
        if (timer == null) return;

        timerTransaction++;
        timer.stop();
        timer = null;
    }

    private synchronized void playSequence() {
        if (phase != Phase.IDLE || winManager.isGameOver()) return;

        phase = Phase.PLAYING;

        manager.reset();
        manager.extendSequence();

        sequencePlayer.setPeriodTicks(12 - manager.sequenceLength() / 2);
        sequencePlayer.play().whenComplete(this::beginReplay);
    }

    private synchronized void beginReplay() {
        if (phase != Phase.PLAYING || winManager.isGameOver()) return;

        phase = Phase.REPLAY;

        commons().announcer().announceSubtitle("game.ap2.mimicry.repeat");

        manager.setReplay(true);

        Translations translations = gameHandle.getTranslations();
        var subject = translations.translateText(gameHandle.getGameInfo().getTaskKey());

        timer = commons().createTimer(subject, calcReplaySeconds());

        int transaction = timerTransaction;

        timer.whenDone(() -> {
            if (transaction == timerTransaction) {
                endReplayAndEliminate();
            }
        });
    }

    private synchronized void endReplayAndEliminate() {
        if (phase != Phase.REPLAY || winManager.isGameOver()) return;

        manager.getPlayersToEliminate().forEach(this::softEliminate);

        onRoundOver();

        gameHandle.getGameScheduler().timeout(this::nextSequence, Ticks.seconds(NEXT_ROUND_DELAY_SECONDS));
    }

    private synchronized void onRoundOver() {
        if (phase != Phase.REPLAY) return;

        phase = Phase.IDLE;

        removeTimer();

        manager.setReplay(false);

        // remove all players who were marked as eliminated this round
        pseudoElimination.commit();
    }

    private int calcReplaySeconds() {
        return Math.max(REPLAY_MIN_SECONDS, Math.min(REPLAY_MAX_SECONDS, manager.sequenceLength() * REPLAY_SECONDS_PER_NOTE));
    }

    private void onCompleted(ServerPlayerEntity player) {
        commons().addScore(player, 1, dataContainer);

        checkRoundComplete();
    }

    private void onAllCompleted() {
        if (phase != Phase.REPLAY) return;

        onRoundOver();

        gameHandle.getGameScheduler().timeout(this::nextSequence, 30);
    }

    private synchronized void softEliminate(ServerPlayerEntity player) {
        if (phase != Phase.REPLAY || !pseudoElimination.eliminate(player)) return;

        checkRoundComplete();
    }

    private synchronized void checkRoundComplete() {
        if (phase != Phase.REPLAY) return;

        int notCompleted = manager.getPlayersToEliminate().size();

        if (notCompleted == 0) {
            onAllCompleted();
            return;
        }

        int remainingReplay = notCompleted - pseudoElimination.size();

        if (remainingReplay <= 0) {
            endReplayAndEliminate();
        }
    }

    private enum Phase { PLAYING, REPLAY, IDLE }
}
