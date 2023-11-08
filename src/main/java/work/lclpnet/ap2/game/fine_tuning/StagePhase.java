package work.lclpnet.ap2.game.fine_tuning;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.block.enums.Instrument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import org.json.JSONArray;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.fine_tuning.melody.FakeNoteBlockPlayer;
import work.lclpnet.ap2.game.fine_tuning.melody.Melody;
import work.lclpnet.ap2.game.fine_tuning.melody.MelodyPlayer;
import work.lclpnet.ap2.game.fine_tuning.melody.Note;
import work.lclpnet.ap2.impl.game.PlayerUtil;
import work.lclpnet.ap2.impl.game.data.ScoreTimeDataContainer;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.SoundHelper;
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
    private final ScoreTimeDataContainer data;
    private final Consumer<Optional<ServerPlayerEntity>> winnerAction;
    private final Melody[] melodies;
    private final GameMap map;
    private final ServerWorld world;
    private BlockPos presenterPos;
    private BlockPos[] presenterNoteBlocks;
    private int[] notes;
    private Instrument[] instruments;
    private FakeNoteBlockPlayer nbPlayer;
    private int melodyNumber = 0;

    public StagePhase(MiniGameHandle gameHandle, ScoreTimeDataContainer data, Melody[] melodies, GameMap map,
                      ServerWorld world, Consumer<Optional<ServerPlayerEntity>> winnerAction) {
        this.gameHandle = gameHandle;
        this.data = data;
        this.melodies = melodies;
        this.map = map;
        this.world = world;
        this.winnerAction = winnerAction;
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

        gameHandle.getScheduler().timeout(this::beginSongPresentation, Ticks.seconds(5));
    }

    private void readStageProps() {
        this.presenterPos = MapUtil.readBlockPos(map.requireProperty("presenter-pos"));

        JSONArray json = map.requireProperty("presenter-note-blocks");
        this.presenterNoteBlocks = FineTuningSetup.readNoteBlockLocations(json, gameHandle.getLogger());

        this.notes = new int[presenterNoteBlocks.length];
        Arrays.fill(this.notes, Note.FIS3.ordinal());

        this.instruments = new Instrument[presenterNoteBlocks.length];
        Arrays.fill(this.instruments, Instrument.HARP);

        this.nbPlayer = new FakeNoteBlockPlayer(world, presenterNoteBlocks, notes, instruments);
    }

    private void beginSongPresentation() {
        MinecraftServer server = gameHandle.getServer();
        TranslationService translations = gameHandle.getTranslations();

        SoundHelper.playSound(server, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.RECORDS, 0.5f, 0f);

        translations.translateText("game.ap2.fine_tuning.presentation").formatted(Formatting.AQUA)
                .acceptEach(PlayerLookup.all(server), (player, text)
                        -> Title.get(player).title(text, Text.empty(), 5, 30, 5));

        gameHandle.getScheduler().timeout(this::presentNextSong, 40);
    }

    private void presentNextSong() {
        MinecraftServer server = gameHandle.getServer();
        TranslationService translations = gameHandle.getTranslations();

        SoundHelper.playSound(server, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.RECORDS, 0.5f, 0f);

        translations.translateText("game.ap2.fine_tuning.present_melody",
                        styled("#" + (melodyNumber + 1), Formatting.YELLOW))
                .formatted(Formatting.AQUA)
                .acceptEach(PlayerLookup.all(server), (player, text)
                        -> Title.get(player).title(text, Text.empty(), 5, 30, 5));

        gameHandle.getScheduler().timeout(this::playOriginalMelody, 40);
    }

    private void playOriginalMelody() {
        Melody melody = melodies[melodyNumber];
        MinecraftServer server = gameHandle.getServer();

        setMelody(melody);

        var melodyPlayer = new MelodyPlayer(note -> {
            for (ServerPlayerEntity player : PlayerLookup.all(server)) {
                nbPlayer.playAtPlayerPos(player, note);
            }
        }, melody.notes().length);

        TaskScheduler scheduler = gameHandle.getScheduler();

        scheduler.interval(melodyPlayer, 1)
                .whenComplete(() -> scheduler.timeout(this::playBestMelody, Ticks.seconds(2)));
    }

    private void setMelody(Melody melody) {
        FakeNoteBlockPlayer.setMelody(melody, notes, instruments);
    }

    private void playBestMelody() {

    }

    private void dispatchWin() {
        winnerAction.accept(data.getBestPlayer(gameHandle.getServer()));
    }
}
