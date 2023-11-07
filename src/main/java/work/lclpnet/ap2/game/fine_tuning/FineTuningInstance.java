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
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.map.MapFacade;
import work.lclpnet.ap2.game.fine_tuning.melody.*;
import work.lclpnet.ap2.impl.game.BootstrapMapOptions;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.ap2.impl.game.data.ScoreDataContainer;
import work.lclpnet.ap2.impl.util.SoundHelper;
import work.lclpnet.ap2.impl.util.heads.PlayerHeadUtil;
import work.lclpnet.ap2.impl.util.heads.PlayerHeads;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks;
import work.lclpnet.kibu.hook.util.PlayerUtils;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.bossbar.BossBarProvider;
import work.lclpnet.lobby.game.util.BossBarTimer;

import java.util.*;

public class FineTuningInstance extends DefaultGameInstance {

    private static final int MELODY_COUNT = 3;
    public static final int REPLAY_COOLDOWN = Ticks.seconds(5);
    private final ScoreDataContainer data = new ScoreDataContainer();
    private final Random random = new Random();
    private final MelodyProvider melodyProvider = new SimpleMelodyProvider(random, new SimpleNotesProvider(random), 5);
    private final Set<UUID> replaying = new HashSet<>();
    private FineTuningSetup setup;
    private Map<UUID, FineTuningRoom> rooms;
    private boolean playersCanInteract = false;
    private Melody melody = null;
    private int melodyNumber = 0;

    public FineTuningInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        setDefaultGameMode(GameMode.SURVIVAL);  // survival is needed to left-click note blocks
    }

    @Override
    protected DataContainer getData() {
        return data;
    }

    @Override
    protected void openMap() {
        MapFacade mapFacade = gameHandle.getMapFacade();
        Identifier gameId = gameHandle.getGameInfo().getId();

        mapFacade.openRandomMap(gameId, new BootstrapMapOptions((world, map) -> {
            setup = new FineTuningSetup(gameHandle, map, world);
            return setup.createRooms();
        }), this::onMapReady);
    }

    @Override
    protected void prepare() {
        var noteBlockLocations = setup.readNoteBlockLocations();
        setup.teleportParticipants(noteBlockLocations);

        rooms = setup.getRooms();

        HookRegistrar hooks = gameHandle.getHookRegistrar();

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

    @Override
    protected void ready() {
        beginListen();
    }

    private void beginListen() {
        MinecraftServer server = gameHandle.getServer();
        TranslationService translations = gameHandle.getTranslations();

        SoundHelper.playSound(server, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.RECORDS, 0.5f, 0f);

        translations.translateText("game.ap2.fine_tuning.listen").formatted(Formatting.DARK_GREEN)
                .acceptEach(PlayerLookup.all(server), (player, text)
                        -> Title.get(player).title(Text.empty(), text, 5, 30, 5));

        gameHandle.getScheduler().timeout(this::playNextMelody, 40);
    }

    private void playNextMelody() {
        melody = melodyProvider.nextMelody();
        rooms.values().forEach(room -> room.setMelody(melody));

        playMelody(this::listenAgain);
    }

    private void playMelody(Runnable onDone) {
        Participants participants = gameHandle.getParticipants();
        TaskScheduler scheduler = gameHandle.getScheduler();

        MelodyPlayer task = new MelodyPlayer(note -> {
            for (ServerPlayerEntity player : participants) {
                FineTuningRoom room = rooms.get(player.getUuid());
                if (room == null) continue;

                room.playNote(player, note);
            }
        }, melody.notes().length);

        scheduler.interval(task, 1)
                .whenComplete(() -> scheduler.timeout(onDone, 20));
    }

    private void playMelodyTo(ServerPlayerEntity player, Runnable onDone) {
        UUID uuid = player.getUuid();
        FineTuningRoom room = rooms.get(uuid);

        if (room == null) return;

        Melody before = room.getCurrentMelody();
        room.setMelody(melody);

        TaskScheduler scheduler = gameHandle.getScheduler();

        MelodyPlayer task = new MelodyPlayer(note -> {
            if (!player.isAlive()) return;

            room.playNote(player, note);
        }, melody.notes().length);

        scheduler.interval(task, 1)
                .whenComplete(() -> {
                    room.setMelody(before);
                    onDone.run();
                });
    }

    private void listenAgain() {
        MinecraftServer server = gameHandle.getServer();
        TranslationService translations = gameHandle.getTranslations();

        SoundHelper.playSound(server, SoundEvents.ENTITY_CHICKEN_EGG, SoundCategory.RECORDS, 0.5f, 0f);

        translations.translateText("game.ap2.fine_tuning.listen_again").formatted(Formatting.DARK_GREEN)
                .acceptEach(PlayerLookup.all(server), (player, text)
                        -> Title.get(player).title(Text.empty(), text, 5, 30, 5));

        gameHandle.getScheduler().timeout(() -> playMelody(this::beginTune), 40);
    }

    private void beginTune() {
        MinecraftServer server = gameHandle.getServer();
        TranslationService translations = gameHandle.getTranslations();
        TaskScheduler scheduler = gameHandle.getScheduler();
        BossBarProvider bossBarProvider = gameHandle.getBossBarProvider();

        var players = PlayerLookup.all(server);

        translations.translateText("game.ap2.fine_tuning.repeat").formatted(Formatting.GREEN)
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

            if (++melodyNumber == MELODY_COUNT) {
                beginStage();
            } else {
                beginListen();
            }
        });
    }

    private void giveReplayItems() {
        TranslationService translations = gameHandle.getTranslations();
        Participants participants = gameHandle.getParticipants();

        for (ServerPlayerEntity player : participants) {
            ItemStack stack = PlayerHeadUtil.getItem(PlayerHeads.GEODE_ARROW_FORWARD);
            stack.setCustomName(translations.translateText(player, "game.ap2.fine_tuning.replay")
                    .styled(style -> style.withItalic(false).withFormatting(Formatting.YELLOW)));

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

    private void beginStage() {
        System.out.println("over");
    }

    private void addNoteBlockHooks() {
        Participants participants = gameHandle.getParticipants();
        HookRegistrar hooks = gameHandle.getHookRegistrar();

        hooks.registerHook(PlayerInteractionHooks.USE_BLOCK, (player, world, hand, hitResult) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.FAIL;
            }

            if (!canInteract(player) || !participants.isParticipating(serverPlayer)) {
                return cancel(serverPlayer);
            }

            BlockPos pos = hitResult.getBlockPos();
            BlockState state = world.getBlockState(pos);

            if (state.isOf(Blocks.NOTE_BLOCK)) {
                FineTuningRoom room = rooms.get(player.getUuid());

                if (room != null) {
                    room.useNoteBlock(serverPlayer, pos);
                }
            } else {
                onUseItem(player);
            }

            return cancel(serverPlayer);
        });

        hooks.registerHook(PlayerInteractionHooks.ATTACK_BLOCK, (player, world, hand, pos, direction) -> {
            if (!canInteract(player) || !(player instanceof ServerPlayerEntity serverPlayer)
                || !participants.isParticipating(serverPlayer)) return ActionResult.FAIL;

            BlockState state = world.getBlockState(pos);

            if (!state.isOf(Blocks.NOTE_BLOCK)) return ActionResult.FAIL;

            FineTuningRoom room = rooms.get(player.getUuid());

            if (room != null) {
                room.playNoteBlock(serverPlayer, pos);
            }

            return ActionResult.FAIL;
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
        replaying.add(uuid);

        playMelodyTo(serverPlayer, () -> replaying.remove(uuid));

        return true;
    }

    private Melody baseMelody() {
        var notes = new Note[8];
        Arrays.fill(notes, Note.FIS3);

        return new Melody(melody.instrument(), notes);
    }

    private boolean canInteract(PlayerEntity player) {
        return playersCanInteract && !replaying.contains(player.getUuid());
    }

    private static ActionResult cancel(ServerPlayerEntity player) {
        PlayerUtils.syncPlayerItems(player);
        return ActionResult.FAIL;
    }
}
