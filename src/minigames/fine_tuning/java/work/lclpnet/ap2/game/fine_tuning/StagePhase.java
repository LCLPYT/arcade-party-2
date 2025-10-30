package work.lclpnet.ap2.game.fine_tuning;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.block.enums.NoteBlockInstrument;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.json.JSONArray;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.game.fine_tuning.melody.FakeNoteBlockPlayer;
import work.lclpnet.ap2.game.fine_tuning.melody.Melody;
import work.lclpnet.ap2.game.fine_tuning.melody.Note;
import work.lclpnet.ap2.game.fine_tuning.melody.PlayMelodyTask;
import work.lclpnet.ap2.impl.game.PlayerUtil;
import work.lclpnet.ap2.impl.game.WinManager;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.ColorUtil;
import work.lclpnet.ap2.impl.util.SoundHelper;
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.game.api.WorldFacade;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static java.lang.Math.abs;
import static java.lang.Math.signum;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

class StagePhase {

    private final MiniGameHandle gameHandle;
    private final MelodyRecords records;
    private final GameMap map;
    private final ServerWorld world;
    private final WinManager<ServerPlayerEntity, PlayerRef> winManager;
    private final SimpleMovementBlocker movementBlocker;
    private final Set<UUID> displays = new HashSet<>();
    private BlockPos presenterPos;
    private float presenterYaw;
    private FakeNoteBlockPlayer nbPlayer;
    private int melodyNumber = 0;

    public StagePhase(MiniGameHandle gameHandle, MelodyRecords records, GameMap map, ServerWorld world,
                      WinManager<ServerPlayerEntity, PlayerRef> winManager) {
        this.gameHandle = gameHandle;
        this.records = records;
        this.map = map;
        this.world = world;
        this.winManager = winManager;
        this.movementBlocker = new SimpleMovementBlocker(gameHandle.getRootScheduler());
        this.movementBlocker.setModifySpeedAttribute(false);
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

        movementBlocker.init(gameHandle.getHooks());

        gameHandle.getScheduler().timeout(this::beginSongPresentation, Ticks.seconds(5));
    }

    private void readStageProps() {
        this.presenterPos = MapUtil.readBlockPos(map.requireProperty("presenter-pos"));
        this.presenterYaw = MapUtil.readAngle(map.requireProperty("presenter-yaw"));

        JSONArray json = map.requireProperty("presenter-note-blocks");
        BlockPos[] presenterNoteBlocks = FineTuningSetup.readNoteBlockLocations(json, gameHandle.getLogger());

        int[] notes = new int[presenterNoteBlocks.length];
        Arrays.fill(notes, Note.FIS3.ordinal());

        NoteBlockInstrument[] instruments = new NoteBlockInstrument[presenterNoteBlocks.length];
        Arrays.fill(instruments, NoteBlockInstrument.HARP);

        this.nbPlayer = new FakeNoteBlockPlayer(presenterNoteBlocks, notes, instruments);
    }

    private void beginSongPresentation() {
        MinecraftServer server = gameHandle.getServer();
        Translations translations = gameHandle.getTranslations();

        SoundHelper.playSound(server, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.RECORDS, 0.5f, 0f);

        translations.translateText("game.ap2.fine_tuning.presentation")
                .formatted(Formatting.DARK_GREEN)
                .acceptEach(PlayerLookup.all(server), (player, text)
                        -> Title.get(player).title(text, Text.empty(), 5, 30, 5));

        gameHandle.getScheduler().timeout(this::presentNextMelody, 40);
    }

    private void presentNextMelody() {
        MinecraftServer server = gameHandle.getServer();
        Translations translations = gameHandle.getTranslations();

        SoundHelper.playSound(server, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.RECORDS, 0.5f, 0f);

        translations.translateText("game.ap2.fine_tuning.present_melody",
                        styled("#" + (melodyNumber + 1), Formatting.YELLOW))
                .formatted(Formatting.AQUA)
                .acceptEach(PlayerLookup.all(server), (player, text)
                        -> Title.get(player).title(text, Text.empty(), 5, 30, 5));

        gameHandle.getScheduler().timeout(this::playOriginalMelody, 40);
    }

    private void playOriginalMelody() {
        Melody melody = records.getMelody(melodyNumber);
        playMelody(melody, new int[0], this::beginBestMelody);
    }

    private void playMelody(Melody melody, int[] offsets, Runnable onDone) {
        MinecraftServer server = gameHandle.getServer();

        setMelody(melody);

        var melodyPlayer = new PlayMelodyTask(note -> {
            for (ServerPlayerEntity player : PlayerLookup.all(server)) {
                nbPlayer.playAtPlayerPos(player, note);
            }

            if (note < 0 || note >= offsets.length) return;

            int noteOffset = offsets[note];

            Vec3d pos = nbPlayer.getNoteBlock(note).toCenterPos();
            Vec3d dir = snapToAxis(presenterPos.toCenterPos().subtract(pos));

            Text label;

            if (noteOffset == 0) {
                label = Text.literal("✅").formatted(Formatting.GREEN);
            } else {
                float error = (abs(noteOffset) - 1) / (float) (Note.values().length - 1) * 2.2f;
                int color = ColorUtil.lerpRgb(0xb2ef09, 0x890404, error);

                label = Text.literal((noteOffset > 0 ? "+" : "") + noteOffset).withColor(color);
            }

            var display = new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world);
            display.setPosition(pos.add(dir.multiply(0.6)));
            display.setText(label);
            display.setBackground(0);
            display.setTransformation(new AffineTransformation(new Matrix4f()
                    .scale(2)
                    .rotateTowards((float) dir.getX(), (float) dir.getY(), (float) dir.getZ(), 0, 1, 0)
                    .translate(0, -1 / 8f, 0)));

            world.spawnEntity(display);

            displays.add(display.getUuid());
        }, melody.notes().length);

        TaskScheduler scheduler = gameHandle.getScheduler();

        scheduler.interval(melodyPlayer, 1).whenComplete(() -> scheduler.timeout(Ticks.seconds(2), () -> {
            for (UUID id : displays) {
                Entity entity = world.getEntity(id);

                if (entity != null) {
                    entity.discard();
                }
            }

            displays.clear();
            onDone.run();
        }));
    }

    private Vec3d snapToAxis(Vec3d dir) {
        double ax = abs(dir.getX());
        double ay = abs(dir.getY());
        double az = abs(dir.getZ());

        if (ax > ay && ax > az) {
            return new Vec3d(signum(dir.getX()), 0, 0);
        } else if (az > ay) {
            return new Vec3d(0, 0, signum(dir.getZ()));
        } else {
            return new Vec3d(0, signum(dir.getY()), 0);
        }
    }

    private void beginBestMelody() {
        MinecraftServer server = gameHandle.getServer();
        Translations translations = gameHandle.getTranslations();

        SoundHelper.playSound(server, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.RECORDS, 0.5f, 0f);

        translations.translateText("game.ap2.fine_tuning.best_was")
                .formatted(Formatting.GREEN)
                .acceptEach(PlayerLookup.all(server), (player, text)
                        -> Title.get(player).title(Text.empty(), text, 5, 50, 0));

        gameHandle.getScheduler().timeout(this::announceBest, 55);
    }

    private void announceBest() {
        var bestMelody = records.getBestMelody(melodyNumber);
        PlayerRef bestRef = bestMelody.playerRef();
        MutableText name = Text.literal(bestRef.name()).formatted(Formatting.GREEN);

        MinecraftServer server = gameHandle.getServer();
        SoundHelper.playSound(server, SoundEvents.UI_LOOM_TAKE_RESULT, SoundCategory.NEUTRAL, 0.5f, 1f);

        ServerPlayerEntity player = announcePlayerAndGet(server, name, bestRef);
        WorldFacade worldFacade = gameHandle.getWorldFacade();

        gameHandle.getScheduler().timeout(() -> playMelody(bestMelody.melody(), bestMelody.offsets(), () -> {
            if (player != null) {
                movementBlocker.enableMovement(player);
                worldFacade.teleport(player);
            }

            beginWorstMelody();
        }), 40);
    }

    private void beginWorstMelody() {
        MinecraftServer server = gameHandle.getServer();
        Translations translations = gameHandle.getTranslations();

        SoundHelper.playSound(server, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.RECORDS, 0.5f, 0f);

        translations.translateText("game.ap2.fine_tuning.worst_was")
                .formatted(Formatting.RED)
                .acceptEach(PlayerLookup.all(server), (player, text)
                        -> Title.get(player).title(Text.empty(), text, 5, 30, 5));

        gameHandle.getScheduler().timeout(this::announceWorst, 40);
    }

    private void announceWorst() {
        var worstMelody = records.getWorstMelody(melodyNumber);
        PlayerRef worstRef = worstMelody.playerRef();
        MutableText name = Text.literal(worstRef.name()).formatted(Formatting.RED);

        MinecraftServer server = gameHandle.getServer();
        SoundHelper.playSound(server, SoundEvents.UI_LOOM_TAKE_RESULT, SoundCategory.NEUTRAL, 0.5f, 0f);

        ServerPlayerEntity player = announcePlayerAndGet(server, name, worstRef);
        WorldFacade worldFacade = gameHandle.getWorldFacade();

        gameHandle.getScheduler().timeout(() -> playMelody(worstMelody.melody(), worstMelody.offsets(), () -> {
            if (player != null) {
                movementBlocker.enableMovement(player);
                worldFacade.teleport(player);
            }

            if (++melodyNumber == FineTuningInstance.MELODY_COUNT) {
                winManager.complete();
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
            player.teleport(world, presenterPos.getX() + 0.5, presenterPos.getY(), presenterPos.getZ() + 0.5, Set.of(), presenterYaw, 0, true);
            movementBlocker.disableMovement(player);
        }

        return player;
    }

    private void setMelody(Melody melody) {
        nbPlayer.setMelody(melody);
    }
}
