package work.lclpnet.ap2.game.fine_tuning;

import lombok.Getter;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.util.heads.PlayerHead;
import work.lclpnet.ap2.game.fine_tuning.melody.*;
import work.lclpnet.ap2.impl.game.GameCommons;
import work.lclpnet.ap2.impl.game.data.IntDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.util.ApRegistries;
import work.lclpnet.ap2.impl.util.BookUtil;
import work.lclpnet.ap2.impl.util.heads.PlayerHeads;
import work.lclpnet.gaco.dynamic_entities.DynamicEntityManager;
import work.lclpnet.game.impl.prot.ProtectionTypes;
import work.lclpnet.game.util.BossBarTimer;
import work.lclpnet.kibu.access.entity.ServerPlayerAccess;
import work.lclpnet.kibu.hook.HookContainer;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks;
import work.lclpnet.kibu.hook.util.PlayerUtils;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskHandle;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.bossbar.BossBarProvider;

import java.util.*;

import static net.minecraft.ChatFormatting.*;
import static work.lclpnet.ap2.game.fine_tuning.FineTuningInstance.MELODY_COUNT;

class TuningPhase {

    public static final int TUNING_TIME_SECONDS = 36;

    private final MiniGameHandle gameHandle;
    private final Map<UUID, FineTuningRoom> rooms;
    private final IntDataContainer<ServerPlayer, PlayerRef> data;
    private final Runnable onEnd;
    private final GameCommons commons;
    private final ServerLevel world;
    private final Random random = new Random();
    private final MelodyProvider melodyProvider = new SimpleMelodyProvider(random, new SimpleNotesProvider(random), 5);
    private final Map<UUID, TaskHandle> replaying = new HashMap<>();
    private final LinkedHashSet<UUID> lastInteracted = new LinkedHashSet<>();
    private final Set<UUID> completed = new HashSet<>();
    private final HookContainer hooks = new HookContainer();
    @Getter
    private final MelodyRecords records = new MelodyRecords();
    private boolean playersCanInteract = false;
    private Melody melody = null;
    private DynamicEntityManager dynamicEntityManager = null;
    private int melodyNumber = 0;
    private BossBarTimer timer;

    public TuningPhase(MiniGameHandle gameHandle, Map<UUID, FineTuningRoom> rooms,
                       IntDataContainer<ServerPlayer, PlayerRef> data, Runnable onEnd, GameCommons commons,
                       ServerLevel world) {
        this.gameHandle = gameHandle;
        this.rooms = rooms;
        this.data = data;
        this.onEnd = onEnd;
        this.commons = commons;
        this.world = world;
    }

    public void init() {
        gameHandle.whenDone(this::unload);

        addNoteBlockHooks();
        PlayerInventoryHooks.MODIFY_INVENTORY.registerWith(hooks, event -> !event.player().canUseGameMasterBlocks());

        PlayerInteractionHooks.USE_ITEM.registerWith(hooks, (player, _, _) -> {
            if (onUseItem(player)) {
                return InteractionResult.SUCCESS_SERVER;
            }

            return InteractionResult.PASS;
        });

        PlayerInteractionHooks.ATTACK_BLOCK.registerWith(hooks, (player, _, _, _, _) -> {
            onUseItem(player);
            return InteractionResult.PASS;
        });
        
        gameHandle.protect(config -> ProtectionTypes.USE_BLOCK.allow(config, (entity, pos) -> {
            BlockState state = entity.level().getBlockState(pos);
            return state.is(Blocks.NOTE_BLOCK) || state.is(BlockTags.ALL_SIGNS);
        }));

        dynamicEntityManager = new DynamicEntityManager(world);
        dynamicEntityManager.init(gameHandle.getScheduler(), gameHandle.getHooks());
    }

    private void addNoteBlockHooks() {
        Participants participants = gameHandle.getParticipants();

        PlayerInteractionHooks.USE_BLOCK.registerWith(hooks, (_player, world, _, hitResult) -> {
            if (!(_player instanceof ServerPlayer player)) {
                return InteractionResult.FAIL;
            }

            if (cannotInteract(player) || !participants.isParticipating(player)) {
                return cancel(player);
            }

            BlockPos pos = hitResult.getBlockPos();
            BlockState state = world.getBlockState(pos);

            if (state.is(Blocks.NOTE_BLOCK)) {
                onUseNoteBlock(player, pos);
            } else if (state.is(BlockTags.ALL_SIGNS)) {
                onUseSign(player, pos);
            } else {
                onUseItem(player);
            }

            return cancel(player);
        });

        PlayerInteractionHooks.ATTACK_BLOCK.registerWith(hooks, (_player, world, _, pos, _) -> {
            if (cannotInteract(_player) || !(_player instanceof ServerPlayer player)
                || !participants.isParticipating(player)) return InteractionResult.FAIL;

            BlockState state = world.getBlockState(pos);

            if (state.is(Blocks.NOTE_BLOCK)) {
                FineTuningRoom room = rooms.get(player.getUUID());

                if (room != null) {
                    room.playNoteBlock(player, pos);
                }
            } else if (state.is(BlockTags.ALL_SIGNS)) {
                onUseSign(player, pos);
            }

            return InteractionResult.FAIL;
        });
    }

    private void onUseSign(ServerPlayer player, BlockPos pos) {
        FineTuningRoom room = rooms.get(player.getUUID());

        if (room == null || completed.contains(player.getUUID())) return;

        BlockPos testSignPos = room.getTestSignPos();

        if (testSignPos == null || !testSignPos.equals(pos)) return;

        triggerReplay(player);
    }

    private void onUseNoteBlock(ServerPlayer player, BlockPos pos) {
        FineTuningRoom room = rooms.get(player.getUUID());

        if (room == null || completed.contains(player.getUUID())) return;

        room.useNoteBlock(player, pos, dynamicEntityManager);
        markInteraction(player);

        if (!room.isComplete(melody)) return;

        completed.add(player.getUUID());

        ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1f);

        gameHandle.getTranslations().translateText("game.ap2.fine_tuning.completed").formatted(GREEN).sendTo(player);

        if (completed.size() < gameHandle.getParticipants().count()) return;

        timer.stop();
    }

    public void beginListen() {
        commons.announcer().announceSubtitle("game.ap2.fine_tuning.listen");

        gameHandle.getScheduler().timeout(this::playNextMelody, 40);
    }

    private void playNextMelody() {
        melody = melodyProvider.nextMelody();
        records.recordMelody(melody);
        rooms.values().forEach(room -> room.setMelody(melody));

        playMelody(this::listenAgain);
    }

    private void playMelody(Runnable onDone) {
        Participants participants = gameHandle.getParticipants();
        TaskScheduler scheduler = gameHandle.getScheduler();

        PlayMelodyTask task = new PlayMelodyTask(note -> {
            for (ServerPlayer player : participants) {
                FineTuningRoom room = rooms.get(player.getUUID());
                if (room == null) continue;

                room.playNote(player, note);
            }
        }, melody.notes().length);

        scheduler.interval(task, 1)
                .whenComplete(() -> scheduler.timeout(onDone, 20));
    }

    private void listenAgain() {
        commons.announcer()
                .withSound(SoundEvents.CHICKEN_EGG, SoundSource.RECORDS, 0.5f, 0f)
                .announceSubtitle("game.ap2.fine_tuning.listen_again");

        gameHandle.getScheduler().timeout(() -> playMelody(this::beginTune), 40);
    }

    private void beginTune() {
        MinecraftServer server = gameHandle.getServer();
        Translations translations = gameHandle.getTranslations();
        TaskScheduler scheduler = gameHandle.getScheduler();
        BossBarProvider bossBarProvider = gameHandle.getBossBarProvider();

        var players = PlayerLookup.all(server);

        translations.translateText("game.ap2.fine_tuning.repeat").formatted(GREEN)
                .acceptEach(players, (player, text)
                        -> Title.get(player).title(Component.empty(), text, 5, 30, 5));

        Melody shuffled = baseMelody();
        rooms.values().forEach(room -> room.setMelody(shuffled));

        playersCanInteract = true;

        giveReplayItems();

        timer = BossBarTimer.builder(translations, translations.translateText("game.ap2.fine_tuning.tune",melodyNumber + 1, MELODY_COUNT))
                .withAlertSound(false)
                .withColor(BossEvent.BossBarColor.RED)
                .withDurationTicks(Ticks.seconds(TUNING_TIME_SECONDS))
                .build();

        timer.addPlayers(players);
        timer.start(bossBarProvider, scheduler);

        timer.whenDone(() -> {
            playersCanInteract = false;
            takeReplayItems();
            stopReplay();

            for (FineTuningRoom room : rooms.values()) {
                room.removeDisplays(dynamicEntityManager);
            }

            evaluateScores(server);
            completed.clear();

            if (++melodyNumber == MELODY_COUNT) {
                onEnd.run();
            } else {
                beginListen();
            }
        });
    }

    private void evaluateScores(MinecraftServer server) {
        PlayerList playerManager = server.getPlayerList();

        Melody baseMelody = baseMelody();
        int bestScore = Integer.MIN_VALUE, worstScore = Integer.MAX_VALUE;
        ServerPlayer best = null, worst = null;

        for (UUID uuid : participantsInteractionOrder()) {
            FineTuningRoom room = rooms.get(uuid);
            if (room == null) continue;

            ServerPlayer player = playerManager.getPlayer(uuid);
            if (player == null) continue;

            int score = room.calculateScore(baseMelody, melody);

            data.addScore(player, score);

            if (score > bestScore) {
                bestScore = score;
                best = player;
            }

            if (score <= worstScore && (score > 0 || worst == null || worst == best)) {
                worstScore = score;
                worst = player;
            }
        }

        lastInteracted.clear();

        if (best == null || worst == null) return;

        FineTuningRoom bestRoom = rooms.get(best.getUUID());
        FineTuningRoom worstRoom = rooms.get(worst.getUUID());

        if (bestRoom == null || worstRoom == null) return;

        records.record(melody, best, bestRoom.getCurrentMelody(), worst, worstRoom.getCurrentMelody());
    }

    private void stopReplay() {
        replaying.values().forEach(TaskHandle::cancel);
        replaying.clear();
    }

    private void giveReplayItems() {
        Translations translations = gameHandle.getTranslations();
        Participants participants = gameHandle.getParticipants();

        PlayerHead head = world.registryAccess()
                .lookupOrThrow(ApRegistries.PLAYER_HEAD)
                .getOptional(PlayerHeads.GEODE_ARROW_FORWARD)
                .orElseThrow();

        for (ServerPlayer player : participants) {
            ItemStack stack = head.createStack();
            stack.set(DataComponents.CUSTOM_NAME, translations.translateText(player, "game.ap2.fine_tuning.replay")
                    .styled(style -> style.withItalic(false).applyFormat(YELLOW)));

            player.getInventory().setItem(4, stack);
        }
    }

    private void takeReplayItems() {
        Participants participants = gameHandle.getParticipants();

        for (ServerPlayer player : participants) {
            player.getInventory().setItem(4, ItemStack.EMPTY);
        }
    }

    private Melody baseMelody() {
        var notes = new Note[8];
        Arrays.fill(notes, Note.FIS3);

        return new Melody(melody.instrument(), notes);
    }

    private void markInteraction(Player player) {
        UUID uuid = player.getUUID();
        lastInteracted.remove(uuid);
        lastInteracted.add(uuid);
    }

    private Iterable<UUID> participantsInteractionOrder() {
        Participants participants = gameHandle.getParticipants();

        var order = new LinkedHashSet<UUID>(participants.count());

        order.addAll(lastInteracted);

        // complement with players who didn't interact

        for (ServerPlayer player : participants) {
            order.add(player.getUUID());
        }

        return order;
    }

    private boolean cannotInteract(Player player) {
        return !playersCanInteract || replaying.containsKey(player.getUUID());
    }

    private static InteractionResult cancel(ServerPlayer player) {
        PlayerUtils.syncPlayerItems(player);
        return InteractionResult.FAIL;
    }

    @Nullable
    private TaskHandle replayMelody(ServerPlayer player, Runnable onDone) {
        UUID uuid = player.getUUID();
        FineTuningRoom room = rooms.get(uuid);

        if (room == null) return null;

        room.setTemporaryMelody(melody);
        room.removeDisplays(dynamicEntityManager);

        TaskScheduler scheduler = gameHandle.getScheduler();

        PlayMelodyTask task = new PlayMelodyTask(note -> {
            if (!player.isAlive()) return;

            room.playNote(player, note);
        }, melody.notes().length);

        return scheduler.interval(task, 1).whenComplete(() -> {
            room.restoreMelody();
            room.markErrors(baseMelody(), melody, dynamicEntityManager, player);
            onDone.run();
        });
    }

    private boolean onUseItem(Player player) {
        Participants participants = gameHandle.getParticipants();

        if (!(player instanceof ServerPlayer serverPlayer) || !participants.isParticipating(serverPlayer)) {
            return false;
        }

        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);

        if (!(stack.is(Items.PLAYER_HEAD)) || completed.contains(player.getUUID())) {
            return false;
        }

        return triggerReplay(serverPlayer);
    }

    private boolean triggerReplay(ServerPlayer serverPlayer) {
        UUID uuid = serverPlayer.getUUID();

        if (replaying.containsKey(uuid)) return false;

        TaskHandle handle = replayMelody(serverPlayer, () -> replaying.remove(uuid));

        replaying.put(uuid, handle);

        return true;
    }

    public void unload() {
        hooks.unload();
    }

    public void giveBooks() {
        Translations translations = gameHandle.getTranslations();
        Participants participants = gameHandle.getParticipants();

        for (ServerPlayer player : participants) {
            ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);

            String controls = translations.translate(player, "game.ap2.fine_tuning.controls.title");

            BookUtil.builder(controls, ApConstants.PERSON_LCLP)
                    .addPage(translations.translateText(player, "game.ap2.fine_tuning.controls.note_up")
                                    .formatted(DARK_BLUE, BOLD).append(":\n"),
                            Component.keybind("key.use").withStyle(DARK_GREEN).append("\n\n"),
                            translations.translateText(player, "game.ap2.fine_tuning.controls.note_down")
                                    .formatted(DARK_BLUE, BOLD).append(":\n"),
                            Component.keybind("key.sneak").withStyle(DARK_GREEN).append(" + ")
                                    .append(Component.keybind("key.use").append("\n\n")),
                            translations.translateText(player, "game.ap2.fine_tuning.controls.test")
                                    .formatted(DARK_BLUE, BOLD).append(":\n"),
                            Component.keybind("key.attack").withStyle(DARK_GREEN))
                    .applyTo(stack);

            stack.set(DataComponents.CUSTOM_NAME, Component.literal(controls)
                    .withStyle(style -> style.withItalic(false).applyFormat(GREEN)));

            player.getInventory().setItem(8, stack);
        }
    }
}
