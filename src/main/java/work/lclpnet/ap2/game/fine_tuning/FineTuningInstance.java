package work.lclpnet.ap2.game.fine_tuning;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
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
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.bossbar.BossBarProvider;
import work.lclpnet.lobby.game.util.BossBarTimer;

import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class FineTuningInstance extends DefaultGameInstance {

    private final ScoreDataContainer data = new ScoreDataContainer();
    private final Random random = new Random();
    private final MelodyProvider melodyProvider = new SimpleMelodyProvider(random, new DummyNotesProvider(), 5);
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

        Participants participants = gameHandle.getParticipants();
        HookRegistrar hooks = gameHandle.getHookRegistrar();

        hooks.registerHook(PlayerInteractionHooks.USE_BLOCK, (player, world, hand, hitResult) -> {
            if (!playersCanInteract || !(player instanceof ServerPlayerEntity serverPlayer)
                || !participants.isParticipating(serverPlayer)) return ActionResult.FAIL;

            BlockPos pos = hitResult.getBlockPos();
            BlockState state = world.getBlockState(pos);

            if (!state.isOf(Blocks.NOTE_BLOCK)) return ActionResult.FAIL;

            FineTuningRoom room = rooms.get(player.getUuid());

            if (room != null) {
                room.useNoteBlock(serverPlayer, pos);
            }

            return ActionResult.FAIL;
        });

        hooks.registerHook(PlayerInteractionHooks.ATTACK_BLOCK, (player, world, hand, pos, direction) -> {
            if (!playersCanInteract || !(player instanceof ServerPlayerEntity serverPlayer)
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

    @Override
    protected void ready() {
        beginListen();
    }

    @Override
    public void participantRemoved(ServerPlayerEntity player) {

    }

    private void beginListen() {
        MinecraftServer server = gameHandle.getServer();
        TranslationService translations = gameHandle.getTranslations();

        SoundHelper.playSound(server, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.RECORDS, 0.5f, 0f);

        translations.translateText("game.ap2.fine_tuning.listen").formatted(Formatting.DARK_GREEN)
                .acceptEach(PlayerLookup.all(server), (player, text)
                        -> Title.get(player).title(Text.empty(), text, 5, 40, 5));

        gameHandle.getScheduler().timeout(this::playNextMelody, 50);
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
                .whenComplete(() -> scheduler.timeout(onDone, 30));
    }

    private void listenAgain() {
        MinecraftServer server = gameHandle.getServer();
        TranslationService translations = gameHandle.getTranslations();

        SoundHelper.playSound(server, SoundEvents.ENTITY_CHICKEN_EGG, SoundCategory.RECORDS, 0.5f, 0f);

        translations.translateText("game.ap2.fine_tuning.listen_again").formatted(Formatting.DARK_GREEN)
                .acceptEach(PlayerLookup.all(server), (player, text)
                        -> Title.get(player).title(Text.empty(), text, 5, 40, 5));

        gameHandle.getScheduler().timeout(() -> playMelody(this::beginTune), 50);
    }

    private void beginTune() {
        MinecraftServer server = gameHandle.getServer();
        TranslationService translations = gameHandle.getTranslations();
        TaskScheduler scheduler = gameHandle.getScheduler();
        BossBarProvider bossBarProvider = gameHandle.getBossBarProvider();

        var players = PlayerLookup.all(server);

        translations.translateText("game.ap2.fine_tuning.repeat").formatted(Formatting.DARK_GREEN)
                .acceptEach(players, (player, text)
                        -> Title.get(player).title(Text.empty(), text, 5, 40, 5));

        Melody shuffled = shuffledMelody();
        rooms.values().forEach(room -> room.setMelody(shuffled));

        playersCanInteract = true;

        BossBarTimer timer = BossBarTimer.builder(translations, translations.translateText("game.ap2.fine_tuning.tune"))
                .withAlertSound(false)
                .withColor(BossBar.Color.RED)
                .withDurationTicks(Ticks.seconds(25))
                .build();

        timer.addPlayers(players);
        timer.start(bossBarProvider, scheduler);

        timer.whenDone(() -> {
            playersCanInteract = false;

            if (++melodyNumber == 3) {
                beginStage();
            } else {
                beginListen();
            }
        });
    }

    private void beginStage() {
        System.out.println("over");
    }

    private Melody shuffledMelody() {
        Note[] allNotes = Note.values();

        Note[] shuffledNotes = Arrays.stream(melody.notes())
                .mapToInt(note -> random.nextInt(allNotes.length))
                .mapToObj(i -> allNotes[i])
                .toArray(Note[]::new);

        return new Melody(melody.instrument(), shuffledNotes);
    }
}
