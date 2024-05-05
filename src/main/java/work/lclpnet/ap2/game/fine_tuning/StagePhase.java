package work.lclpnet.ap2.game.fine_tuning;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.block.enums.Instrument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.fine_tuning.melody.FakeNoteBlockPlayer;
import work.lclpnet.ap2.game.fine_tuning.melody.Melody;
import work.lclpnet.ap2.game.fine_tuning.melody.Note;
import work.lclpnet.ap2.game.fine_tuning.melody.PlayMelodyTask;
import work.lclpnet.ap2.impl.game.PlayerUtil;
import work.lclpnet.ap2.impl.game.data.ScoreTimeDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.game.data.type.PlayerRefResolver;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.SoundHelper;
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.lobby.game.api.WorldFacade;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;

import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

class StagePhase {

    private final MiniGameHandle gameHandle;
    private final ScoreTimeDataContainer<ServerPlayerEntity, PlayerRef> data;
    private final PlayerRefResolver resolver;
    private final Consumer<Optional<ServerPlayerEntity>> winnerAction;
    private final MelodyRecords records;
    private final GameMap map;
    private final ServerWorld world;
    private final SimpleMovementBlocker movementBlocker;
    private BlockPos presenterPos;
    private float presenterYaw;
    private FakeNoteBlockPlayer nbPlayer;
    private int melodyNumber = 0;

    public StagePhase(MiniGameHandle gameHandle, ScoreTimeDataContainer<ServerPlayerEntity, PlayerRef> data,
                      PlayerRefResolver resolver, MelodyRecords records, GameMap map, ServerWorld world,
                      Consumer<Optional<ServerPlayerEntity>> winnerAction) {
        this.gameHandle = gameHandle;
        this.data = data;
        this.resolver = resolver;
        this.records = records;
        this.map = map;
        this.world = world;
        this.winnerAction = winnerAction;
        this.movementBlocker = new SimpleMovementBlocker(gameHandle.getScheduler());
        this.movementBlocker.setModifyAttributes(false);
    }

    public void beginStage() {
        MinecraftServer server = gameHandle.getServer();
        WorldFacade worldFacade = gameHandle.getWorldFacade();
        PlayerUtil playerUtil = gameHandle.getPlayerUtil();

        playerUtil.setDefaultGameMode(GameMode.ADVENTURE);

        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
            playerUtil.resetPlayer(player);
            worldFacade.teleport(player);
        }

        readStageProps();

        movementBlocker.init(gameHandle.getHookRegistrar());

        gameHandle.getGameScheduler().timeout(this::beginSongPresentation, Ticks.seconds(5));
    }

    private void readStageProps() {
        this.presenterPos = MapUtil.readBlockPos(map.requireProperty("presenter-pos"));
        this.presenterYaw = MapUtil.readAngle(map.requireProperty("presenter-yaw"));

        JSONArray json = map.requireProperty("presenter-note-blocks");
        BlockPos[] presenterNoteBlocks = FineTuningSetup.readNoteBlockLocations(json, gameHandle.getLogger());

        int[] notes = new int[presenterNoteBlocks.length];
        Arrays.fill(notes, Note.FIS3.ordinal());

        Instrument[] instruments = new Instrument[presenterNoteBlocks.length];
        Arrays.fill(instruments, Instrument.HARP);

        this.nbPlayer = new FakeNoteBlockPlayer(world, presenterNoteBlocks, notes, instruments);
    }

    private void beginSongPresentation() {
        MinecraftServer server = gameHandle.getServer();
        TranslationService translations = gameHandle.getTranslations();

        SoundHelper.playSound(server, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.RECORDS, 0.5f, 0f);

        translations.translateText("game.ap2.fine_tuning.presentation")
                .formatted(Formatting.DARK_GREEN)
                .acceptEach(PlayerLookup.all(server), (player, text)
                        -> Title.get(player).title(text, Text.empty(), 5, 30, 5));

        gameHandle.getGameScheduler().timeout(this::presentNextMelody, 40);
    }

    private void presentNextMelody() {
        MinecraftServer server = gameHandle.getServer();
        TranslationService translations = gameHandle.getTranslations();

        SoundHelper.playSound(server, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.RECORDS, 0.5f, 0f);

        translations.translateText("game.ap2.fine_tuning.present_melody",
                        styled("#" + (melodyNumber + 1), Formatting.YELLOW))
                .formatted(Formatting.AQUA)
                .acceptEach(PlayerLookup.all(server), (player, text)
                        -> Title.get(player).title(text, Text.empty(), 5, 30, 5));

        gameHandle.getGameScheduler().timeout(this::playOriginalMelody, 40);
    }

    private void playOriginalMelody() {
        Melody melody = records.getMelody(melodyNumber);
        playMelody(melody, this::beginBestMelody);
    }

    private void playMelody(Melody melody, Runnable onDone) {
        MinecraftServer server = gameHandle.getServer();

        setMelody(melody);

        var melodyPlayer = new PlayMelodyTask(note -> {
            for (ServerPlayerEntity player : PlayerLookup.all(server)) {
                nbPlayer.playAtPlayerPos(player, note);
            }
        }, melody.notes().length);

        TaskScheduler scheduler = gameHandle.getGameScheduler();

        scheduler.interval(melodyPlayer, 1)
                .whenComplete(() -> scheduler.timeout(onDone, Ticks.seconds(2)));
    }

    private void beginBestMelody() {
        MinecraftServer server = gameHandle.getServer();
        TranslationService translations = gameHandle.getTranslations();

        SoundHelper.playSound(server, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.RECORDS, 0.5f, 0f);

        translations.translateText("game.ap2.fine_tuning.best_was")
                .formatted(Formatting.GREEN)
                .acceptEach(PlayerLookup.all(server), (player, text)
                        -> Title.get(player).title(Text.empty(), text, 5, 50, 0));

        gameHandle.getGameScheduler().timeout(this::announceBest, 55);
    }

    private void announceBest() {
        var bestMelody = records.getBestMelody(melodyNumber);
        PlayerRef bestRef = bestMelody.playerRef();
        MutableText name = Text.literal(bestRef.name()).formatted(Formatting.GREEN);

        MinecraftServer server = gameHandle.getServer();
        SoundHelper.playSound(server, SoundEvents.UI_LOOM_TAKE_RESULT, SoundCategory.NEUTRAL, 0.5f, 1f);

        ServerPlayerEntity player = announcePlayerAndGet(server, name, bestRef);
        WorldFacade worldFacade = gameHandle.getWorldFacade();

        gameHandle.getGameScheduler().timeout(() -> playMelody(bestMelody.melody(), () -> {
            if (player != null) {
                movementBlocker.enableMovement(player);
                worldFacade.teleport(player);
            }

            beginWorstMelody();
        }), 40);
    }

    private void beginWorstMelody() {
        MinecraftServer server = gameHandle.getServer();
        TranslationService translations = gameHandle.getTranslations();

        SoundHelper.playSound(server, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.RECORDS, 0.5f, 0f);

        translations.translateText("game.ap2.fine_tuning.worst_was")
                .formatted(Formatting.RED)
                .acceptEach(PlayerLookup.all(server), (player, text)
                        -> Title.get(player).title(Text.empty(), text, 5, 30, 5));

        gameHandle.getGameScheduler().timeout(this::announceWorst, 40);
    }

    private void announceWorst() {
        var worstMelody = records.getWorstMelody(melodyNumber);
        PlayerRef worstRef = worstMelody.playerRef();
        MutableText name = Text.literal(worstRef.name()).formatted(Formatting.RED);

        MinecraftServer server = gameHandle.getServer();
        SoundHelper.playSound(server, SoundEvents.UI_LOOM_TAKE_RESULT, SoundCategory.NEUTRAL, 0.5f, 0f);

        ServerPlayerEntity player = announcePlayerAndGet(server, name, worstRef);
        WorldFacade worldFacade = gameHandle.getWorldFacade();

        gameHandle.getGameScheduler().timeout(() -> playMelody(worstMelody.melody(), () -> {
            if (player != null) {
                movementBlocker.enableMovement(player);
                worldFacade.teleport(player);
            }

            if (++melodyNumber == FineTuningInstance.MELODY_COUNT) {
                dispatchWin();
            } else {
                presentNextMelody();
            }
        }), 40);
    }

    @Nullable
    private ServerPlayerEntity announcePlayerAndGet(MinecraftServer server, MutableText name, PlayerRef ref) {
        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
            Title.get(player).title(name, Text.empty(), 5, 30, 5);
        }

        ServerPlayerEntity player = server.getPlayerManager().getPlayer(ref.uuid());

        if (player != null) {
            player.teleport(world, presenterPos.getX() + 0.5, presenterPos.getY(), presenterPos.getZ() + 0.5, presenterYaw, 0);
            movementBlocker.disableMovement(player);
        }

        return player;
    }

    private void setMelody(Melody melody) {
        nbPlayer.setMelody(melody);
    }

    private void dispatchWin() {
        winnerAction.accept(data.getBestSubject(resolver));
    }
}
