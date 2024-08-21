package work.lclpnet.ap2.game.mimicry;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.game.mimicry.data.MimicryManager;
import work.lclpnet.ap2.game.mimicry.data.MimicryRoom;
import work.lclpnet.ap2.game.mimicry.data.SequencePlayer;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.math.AffineIntMatrix;
import work.lclpnet.ap2.impl.util.world.StackedRoomGenerator;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.mc.KibuBlockPos;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.util.BossBarTimer;

import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class MimicryInstance extends EliminationGameInstance implements MapBootstrap {

    private static final int
            PREPARE_TICKS = 50,
            REPLAY_MIN_SECONDS = 8,
            REPLAY_SECONDS_PER_NOTE = 1,
            REPLAY_MAX_SECONDS = 30,
            NEXT_ROUND_DELAY_SECONDS = 4;

    private MimicryManager manager = null;
    private SequencePlayer sequencePlayer = null;
    private BossBarTimer timer = null;
    private int timerTransaction = 0;

    public MimicryInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        disableTeleportEliminated();
    }

    @Override
    public CompletableFuture<Void> createWorldBootstrap(ServerWorld world, GameMap map) {
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

                    manager = new MimicryManager(gameHandle, rooms, buttons, random, world, this::onAllCompleted);
                })
                .exceptionally(throwable -> {
                    gameHandle.getLogger().error("Failed to create rooms", throwable);
                    return null;
                });
    }

    @Override
    protected void prepare() {
        ServerWorld world = getWorld();

        manager.eachParticipant((player, room) -> room.teleport(player, world));
    }

    @Override
    protected void ready() {
        sequencePlayer = new SequencePlayer(manager, gameHandle.getGameScheduler(), getWorld());

        nextSequence();

        gameHandle.getHookRegistrar().registerHook(PlayerInteractionHooks.USE_BLOCK, this::onUseBlock);
    }

    @Override
    protected void onEliminated(ServerPlayerEntity player) {
        double x = player.getX(), y = player.getY(), z = player.getZ();

        ServerWorld world = getWorld();

        world.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1f, 0f);
        world.spawnParticles(ParticleTypes.LAVA, x, y, z, 100, 0.5, 0.5, 0.5, 0.2);

        putScoreDetail(player);
    }

    @Override
    public void participantRemoved(ServerPlayerEntity player) {
        Participants participants = gameHandle.getParticipants();

        if (participants.count() == 1) {
            participants.stream().findAny().ifPresent(this::putScoreDetail);
        }

        super.participantRemoved(player);
    }

    private void putScoreDetail(ServerPlayerEntity player) {
        int count = manager.getCompletedCount(player);

        var detail = gameHandle.getTranslations().translateText("game.ap2.mimicry.completed", count);
        getData().eliminated(player, detail);
    }

    private ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
        if (winManager.isGameOver()
            || !(player instanceof ServerPlayerEntity serverPlayer)
            || !gameHandle.getParticipants().isParticipating(serverPlayer)) {
            return ActionResult.PASS;
        }

        BlockPos pos = hitResult.getBlockPos();

        if (world.getBlockState(pos).isIn(BlockTags.BUTTONS)) {
            if (manager.onInputButton(serverPlayer, pos)) {
                var msg = gameHandle.getTranslations().translateText(serverPlayer, "game.ap2.mimicry.wrong_button")
                        .formatted(Formatting.RED);

                serverPlayer.sendMessage(msg);

                eliminate(serverPlayer);
            }

            return ActionResult.FAIL;
        }

        return ActionResult.PASS;
    }

    private void nextSequence() {
        if (timer != null) {
            timerTransaction++;
            timer.stop();
        }

        commons().announcer().announceSubtitle("game.ap2.mimicry.attention");

        gameHandle.getGameScheduler().timeout(this::playSequence, PREPARE_TICKS);
    }

    private void playSequence() {
        manager.reset();
        manager.extendSequence();

        sequencePlayer.setPeriodTicks(12 - manager.sequenceLength() / 2);
        sequencePlayer.play().whenComplete(this::beginReplay);
    }

    private void beginReplay() {
        commons().announcer().announceSubtitle("game.ap2.mimicry.repeat");

        manager.setReplay(true);

        Translations translations = gameHandle.getTranslations();
        var subject = translations.translateText(gameHandle.getGameInfo().getTaskKey());

        timer = commons().createTimer(subject, calcReplaySeconds());

        int transaction = timerTransaction;

        timer.whenDone(() -> {
            if (transaction == timerTransaction) {
                endReplay();
            }
        });
    }

    private void endReplay() {
        timer = null;

        eliminateAll(manager.getPlayersToEliminate());

        onRoundOver();

        if (winManager.isGameOver()) return;

        gameHandle.getGameScheduler().timeout(this::nextSequence, Ticks.seconds(NEXT_ROUND_DELAY_SECONDS));
    }

    private void onRoundOver() {
        manager.setReplay(false);
    }

    private int calcReplaySeconds() {
        return Math.max(REPLAY_MIN_SECONDS, Math.min(REPLAY_MAX_SECONDS, manager.sequenceLength() * REPLAY_SECONDS_PER_NOTE));
    }

    private void onAllCompleted() {
        if (timer != null) {
            timerTransaction++;
            timer.stop();
        }

        onRoundOver();

        gameHandle.getGameScheduler().timeout(this::nextSequence, 30);
    }
}
