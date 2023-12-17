package work.lclpnet.ap2.game.fine_tuning;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.ap2.game.fine_tuning.melody.*;
import work.lclpnet.ap2.impl.game.data.ScoreTimeDataContainer;
import work.lclpnet.ap2.impl.util.BookUtil;
import work.lclpnet.ap2.impl.util.SoundHelper;
import work.lclpnet.ap2.impl.util.heads.PlayerHeadUtil;
import work.lclpnet.ap2.impl.util.heads.PlayerHeads;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks;
import work.lclpnet.kibu.hook.util.PlayerUtils;
import work.lclpnet.kibu.plugin.hook.HookContainer;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskHandle;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.bossbar.BossBarProvider;
import work.lclpnet.lobby.game.util.BossBarTimer;
import work.lclpnet.mplugins.ext.Unloadable;

import java.util.*;

import static net.minecraft.util.Formatting.*;
import static work.lclpnet.ap2.game.fine_tuning.FineTuningInstance.MELODY_COUNT;
import static work.lclpnet.ap2.game.fine_tuning.FineTuningInstance.REPLAY_COOLDOWN;

class TuningPhase implements Unloadable {

    private final MiniGameHandle gameHandle;
    private final Map<UUID, FineTuningRoom> rooms;
    private final ScoreTimeDataContainer data;
    private final Runnable onEnd;
    private final Random random = new Random();
    private final MelodyProvider melodyProvider = new SimpleMelodyProvider(random, new SimpleNotesProvider(random), 5);
    private final Map<UUID, TaskHandle> replaying = new HashMap<>();
    private final LinkedHashSet<UUID> lastInteracted = new LinkedHashSet<>();
    private final HookContainer hooks = new HookContainer();
    private final MelodyRecords records = new MelodyRecords();
    private boolean playersCanInteract = false;
    private Melody melody = null;
    private int melodyNumber = 0;

    public TuningPhase(MiniGameHandle gameHandle, Map<UUID, FineTuningRoom> rooms, ScoreTimeDataContainer data, Runnable onEnd) {
        this.gameHandle = gameHandle;
        this.rooms = rooms;
        this.data = data;
        this.onEnd = onEnd;
    }

    public void init() {
        gameHandle.closeWhenDone(this);

        addNoteBlockHooks();
        hooks.registerHook(PlayerInventoryHooks.MODIFY_INVENTORY, event -> !event.player().isCreativeLevelTwoOp());

        hooks.registerHook(PlayerInteractionHooks.USE_ITEM, (player, world, hand) -> {
            if (onUseItem(player)) {
                return TypedActionResult.success(ItemStack.EMPTY, true);
            }

            return TypedActionResult.pass(ItemStack.EMPTY);
        });

        hooks.registerHook(PlayerInteractionHooks.ATTACK_BLOCK, (player, world, hand, pos, direction) -> {
            onUseItem(player);
            return ActionResult.PASS;
        });
    }

    private void addNoteBlockHooks() {
        Participants participants = gameHandle.getParticipants();

        hooks.registerHook(PlayerInteractionHooks.USE_BLOCK, (player, world, hand, hitResult) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.FAIL;
            }

            if (cannotInteract(player) || !participants.isParticipating(serverPlayer)) {
                return cancel(serverPlayer);
            }

            BlockPos pos = hitResult.getBlockPos();
            BlockState state = world.getBlockState(pos);

            if (state.isOf(Blocks.NOTE_BLOCK)) {
                FineTuningRoom room = rooms.get(player.getUuid());

                if (room != null) {
                    if (serverPlayer.isSneaking()) {
                        room.playNoteBlock(serverPlayer, pos);
                    } else {
                        room.useNoteBlock(serverPlayer, pos, true);
                        markInteraction(serverPlayer);
                    }
                }
            } else {
                onUseItem(player);
            }

            return cancel(serverPlayer);
        });

        hooks.registerHook(PlayerInteractionHooks.ATTACK_BLOCK, (player, world, hand, pos, direction) -> {
            if (cannotInteract(player) || !(player instanceof ServerPlayerEntity serverPlayer)
                || !participants.isParticipating(serverPlayer)) return ActionResult.FAIL;

            BlockState state = world.getBlockState(pos);

            if (!state.isOf(Blocks.NOTE_BLOCK)) return ActionResult.FAIL;

            FineTuningRoom room = rooms.get(player.getUuid());

            if (room != null) {
                if (serverPlayer.isSneaking()) {
                    room.playNoteBlock(serverPlayer, pos);
                } else {
                    room.useNoteBlock(serverPlayer, pos, false);
                    markInteraction(serverPlayer);
                }
            }

            return ActionResult.FAIL;
        });
    }

    public void beginListen() {
        MinecraftServer server = gameHandle.getServer();
        TranslationService translations = gameHandle.getTranslations();

        SoundHelper.playSound(server, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.RECORDS, 0.5f, 0f);

        translations.translateText("game.ap2.fine_tuning.listen").formatted(DARK_GREEN)
                .acceptEach(PlayerLookup.all(server), (player, text)
                        -> Title.get(player).title(Text.empty(), text, 5, 30, 5));

        gameHandle.getGameScheduler().timeout(this::playNextMelody, 40);
    }

    private void playNextMelody() {
        melody = melodyProvider.nextMelody();
        records.recordMelody(melody);
        rooms.values().forEach(room -> room.setMelody(melody));

        playMelody(this::listenAgain);
    }

    private void playMelody(Runnable onDone) {
        Participants participants = gameHandle.getParticipants();
        TaskScheduler scheduler = gameHandle.getGameScheduler();

        PlayMelodyTask task = new PlayMelodyTask(note -> {
            for (ServerPlayerEntity player : participants) {
                FineTuningRoom room = rooms.get(player.getUuid());
                if (room == null) continue;

                room.playNote(player, note);
            }
        }, melody.notes().length);

        scheduler.interval(task, 1)
                .whenComplete(() -> scheduler.timeout(onDone, 20));
    }

    private void listenAgain() {
        MinecraftServer server = gameHandle.getServer();
        TranslationService translations = gameHandle.getTranslations();

        SoundHelper.playSound(server, SoundEvents.ENTITY_CHICKEN_EGG, SoundCategory.RECORDS, 0.5f, 0f);

        translations.translateText("game.ap2.fine_tuning.listen_again").formatted(DARK_GREEN)
                .acceptEach(PlayerLookup.all(server), (player, text)
                        -> Title.get(player).title(Text.empty(), text, 5, 30, 5));

        gameHandle.getGameScheduler().timeout(() -> playMelody(this::beginTune), 40);
    }

    private void beginTune() {
        MinecraftServer server = gameHandle.getServer();
        TranslationService translations = gameHandle.getTranslations();
        TaskScheduler scheduler = gameHandle.getGameScheduler();
        BossBarProvider bossBarProvider = gameHandle.getBossBarProvider();

        var players = PlayerLookup.all(server);

        translations.translateText("game.ap2.fine_tuning.repeat").formatted(GREEN)
                .acceptEach(players, (player, text)
                        -> Title.get(player).title(Text.empty(), text, 5, 30, 5));

        Melody shuffled = baseMelody();
        rooms.values().forEach(room -> room.setMelody(shuffled));

        playersCanInteract = true;

        giveReplayItems();

        BossBarTimer timer = BossBarTimer.builder(translations, translations.translateText("game.ap2.fine_tuning.tune"))
                .withAlertSound(false)
                .withColor(BossBar.Color.RED)
                .withDurationTicks(Ticks.seconds(40))
                .build();

        timer.addPlayers(players);
        timer.start(bossBarProvider, scheduler);

        timer.whenDone(() -> {
            playersCanInteract = false;
            takeReplayItems();
            stopReplay();

            evaluateScores(server);

            if (++melodyNumber == MELODY_COUNT) {
                onEnd.run();
            } else {
                beginListen();
            }
        });
    }

    private void evaluateScores(MinecraftServer server) {
        PlayerManager playerManager = server.getPlayerManager();

        int bestScore = Integer.MIN_VALUE, worstScore = Integer.MAX_VALUE;
        ServerPlayerEntity best = null, worst = null;

        for (UUID uuid : participantsInteractionOrder()) {
            FineTuningRoom room = rooms.get(uuid);
            if (room == null) continue;

            ServerPlayerEntity player = playerManager.getPlayer(uuid);
            if (player == null) continue;

            int score = room.calculateScore(melody);

            data.addScore(player, score);

            if (score > bestScore) {
                bestScore = score;
                best = player;
            }

            if (score <= worstScore) {
                worstScore = score;
                worst = player;
            }
        }

        lastInteracted.clear();

        if (best == null) return;

        var bestRoom = rooms.get(best.getUuid());
        var worstRoom = rooms.get(worst.getUuid());

        if (bestRoom == null || worstRoom == null) return;

        records.record(best, bestRoom.getCurrentMelody(), worst, worstRoom.getCurrentMelody());
    }

    private void stopReplay() {
        replaying.values().forEach(TaskHandle::cancel);
        replaying.clear();
    }

    private void giveReplayItems() {
        TranslationService translations = gameHandle.getTranslations();
        Participants participants = gameHandle.getParticipants();

        for (ServerPlayerEntity player : participants) {
            ItemStack stack = PlayerHeadUtil.getItem(PlayerHeads.GEODE_ARROW_FORWARD);
            stack.setCustomName(translations.translateText(player, "game.ap2.fine_tuning.replay")
                    .styled(style -> style.withItalic(false).withFormatting(YELLOW)));

            player.getInventory().setStack(4, stack);
            player.getItemCooldownManager().set(stack.getItem(), REPLAY_COOLDOWN);
        }
    }

    private void takeReplayItems() {
        Participants participants = gameHandle.getParticipants();

        for (ServerPlayerEntity player : participants) {
            player.getInventory().setStack(4, ItemStack.EMPTY);
            player.getItemCooldownManager().remove(Items.PLAYER_HEAD);
        }
    }

    private Melody baseMelody() {
        var notes = new Note[8];
        Arrays.fill(notes, Note.FIS3);

        return new Melody(melody.instrument(), notes);
    }

    private void markInteraction(PlayerEntity player) {
        UUID uuid = player.getUuid();
        lastInteracted.remove(uuid);
        lastInteracted.add(uuid);
    }

    private Iterable<UUID> participantsInteractionOrder() {
        Participants participants = gameHandle.getParticipants();

        var order = new LinkedHashSet<UUID>(participants.count());

        order.addAll(lastInteracted);

        // complement with players who didn't interact

        for (ServerPlayerEntity player : participants) {
            order.add(player.getUuid());
        }

        return order;
    }

    private boolean cannotInteract(PlayerEntity player) {
        return !playersCanInteract || replaying.containsKey(player.getUuid());
    }

    private static ActionResult cancel(ServerPlayerEntity player) {
        PlayerUtils.syncPlayerItems(player);
        return ActionResult.FAIL;
    }

    @Nullable
    private TaskHandle replayMelody(ServerPlayerEntity player, Runnable onDone) {
        UUID uuid = player.getUuid();
        FineTuningRoom room = rooms.get(uuid);

        if (room == null) return null;

        room.setTemporaryMelody(melody);

        TaskScheduler scheduler = gameHandle.getGameScheduler();

        PlayMelodyTask task = new PlayMelodyTask(note -> {
            if (!player.isAlive()) return;

            room.playNote(player, note);
        }, melody.notes().length);

        return scheduler.interval(task, 1)
                .whenComplete(() -> {
                    room.restoreMelody();
                    onDone.run();
                });
    }

    private boolean onUseItem(PlayerEntity player) {
        Participants participants = gameHandle.getParticipants();

        if (!(player instanceof ServerPlayerEntity serverPlayer) || !participants.isParticipating(serverPlayer)) {
            return false;
        }

        ItemStack stack = player.getStackInHand(Hand.MAIN_HAND);

        if (!(stack.isOf(Items.PLAYER_HEAD))) {
            return false;
        }

        ItemCooldownManager cooldownManager = player.getItemCooldownManager();

        if (cooldownManager.isCoolingDown(Items.PLAYER_HEAD)) {
            return false;
        }

        cooldownManager.set(Items.PLAYER_HEAD, REPLAY_COOLDOWN);

        UUID uuid = serverPlayer.getUuid();

        TaskHandle handle = replayMelody(serverPlayer, () -> replaying.remove(uuid));

        replaying.put(uuid, handle);

        return true;
    }

    @Override
    public void unload() {
        hooks.unload();
    }

    public MelodyRecords getRecords() {
        return records;
    }

    public void giveBooks() {
        TranslationService translations = gameHandle.getTranslations();
        Participants participants = gameHandle.getParticipants();

        for (ServerPlayerEntity player : participants) {
            ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);

            String controls = translations.translate(player, "game.ap2.fine_tuning.controls.title");

            BookUtil.builder(controls, ApConstants.PERSON_LCLP)
                    .addPage(translations.translateText(player, "game.ap2.fine_tuning.controls.note_up")
                                    .formatted(DARK_BLUE, BOLD).append(":\n"),
                            Text.keybind("key.use").formatted(DARK_GREEN).append("\n\n"),
                            translations.translateText(player, "game.ap2.fine_tuning.controls.note_down")
                                    .formatted(DARK_BLUE, BOLD).append(":\n"),
                            Text.keybind("key.attack").formatted(DARK_GREEN).append("\n\n"),
                            translations.translateText(player, "game.ap2.fine_tuning.controls.test")
                                    .formatted(DARK_BLUE, BOLD).append(":\n"),
                            Text.keybind("key.sneak").formatted(DARK_GREEN).append(" + ")
                                    .append(Text.keybind("key.use")))
                    .toNbt(stack.getOrCreateNbt());

            stack.setCustomName(Text.literal(controls)
                    .styled(style -> style.withItalic(false).withFormatting(GREEN)));

            player.getInventory().setStack(8, stack);
        }
    }
}
