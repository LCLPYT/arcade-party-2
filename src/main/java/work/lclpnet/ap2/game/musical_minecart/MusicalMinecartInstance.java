package work.lclpnet.ap2.game.musical_minecart;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.util.music.ConfiguredSong;
import work.lclpnet.ap2.api.util.music.PlaybackInfo;
import work.lclpnet.ap2.api.util.music.SongManager;
import work.lclpnet.ap2.game.musical_minecart.cmd.SetSongCommand;
import work.lclpnet.ap2.game.musical_minecart.cmd.SkipSongCommand;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.SoundHelper;
import work.lclpnet.kibu.cmd.type.CommandRegistrar;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskHandle;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.TextTranslatable;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.util.BossBarTimer;
import work.lclpnet.notica.Notica;
import work.lclpnet.notica.api.CheckedSong;
import work.lclpnet.notica.api.PlaybackOptions;
import work.lclpnet.notica.api.PlaybackVariant;
import work.lclpnet.notica.api.SongHandle;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class MusicalMinecartInstance extends EliminationGameInstance {

    private static final boolean
            DEBUG_INFINITE_SONGS = true,
            DEBUG_FULL_DELAY = true,
            DEBUG_INFO = true;

    private static final int
            MIN_DELAY_TICKS = Ticks.seconds(10),
            MAX_DELAY_TICKS = Ticks.seconds(20),  // do not increase; song timings are adjusted to max 20
            ELIMINATION_DELAY_TICKS = Ticks.seconds(9),
            NEXT_SONG_DELAY_TICKS = Ticks.seconds(2),
            PARTICLE_AMOUNT = 2;

    private static final float MUSIC_VOLUME = 0.75f;

    private final MMSongs songManager;
    private final Random random = new Random();
    private final Set<MinecartEntity> minecartEntities = new HashSet<>();
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private BlockBox bounds = null, particleBox = null;
    @Nullable
    private SongHandle songHandle = null;
    private BossBarTimer timer = null;
    private TaskHandle taskHandle = null;

    public MusicalMinecartInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        SongManager songManager = gameHandle.getSongManager();
        Translations translations = gameHandle.getTranslations();

        this.songManager = new MMSongs(songManager, translations, random, gameHandle.getLogger());
    }

    @Override
    protected void prepare() {
        GameMap map = getMap();

        bounds = MapUtil.readBox(map.requireProperty("bounds"));

        if (map.hasProperty("particle", JSONArray.class)) {
            particleBox = MapUtil.readBox(map.requireProperty("particle"));
        }

        songManager.init();

        useRemainingPlayersDisplay();

        gameHandle.protect(config -> config.allow(ProtectionTypes.MOUNT));

        CommandRegistrar commands = gameHandle.getCommandRegistrar();
        new SetSongCommand(songManager, this::skipSong).register(commands);
        new SkipSongCommand(this::skipSong).register(commands);
    }

    @Override
    protected void ready() {
        nextSong();

        gameHandle.getGameScheduler().interval(this::tickParticle, 7);

        ready.set(true);
    }

    private void nextSong() {
        removeMinecarts();

        songManager.getNextSong().whenComplete((res, err) -> {
            if (err == null) {
                this.playSong(res);
                return;
            }

            gameHandle.getLogger().error("Failed to load next song", err);

            getWinManagerAccess().draw();
        });
    }

    private synchronized void playSong(ConfiguredSong config) {
        MinecraftServer server = gameHandle.getServer();

        var players = PlayerLookup.all(server);

        CheckedSong song = config.song();
        PlaybackInfo playback = config.playbackInfo();
        var playbackOptions = new PlaybackOptions(playback.volume() * MUSIC_VOLUME, PlaybackVariant.STREAMED, playback.stereoMode());

        Notica notica = Notica.getInstance(server);
        songHandle = notica.playSong(song, playbackOptions, playback.startTick(), players);

        int delay;

        if (DEBUG_FULL_DELAY) {
            delay = MAX_DELAY_TICKS;
        } else {
            delay = MIN_DELAY_TICKS + random.nextInt(MAX_DELAY_TICKS - MIN_DELAY_TICKS + 1);
        }

        if (DEBUG_INFO) {
            int total = songManager.getSongs().size();
            int done = total - songManager.getQueue().size();

            timer = commons().createTimerTicks("Queue %s / %s".formatted(done, total), delay);
        }

        taskHandle = gameHandle.getGameScheduler().timeout(this::stopMusic, delay);

        TextTranslatable title = songManager.getSongTitle(config.info(), song.song().metaData());

        if (title == null) return;

        gameHandle.getTranslations()
                .translateText("game.ap2.musical_minecart.now_playing", title)
                .formatted(Formatting.GRAY)
                .sendTo(players);
    }

    private synchronized void stopMusic() {
        if (songHandle != null) {
            songHandle.stop();
            songHandle = null;
        }

        if (DEBUG_INFINITE_SONGS) {
            nextSong();
            return;
        }

        spawnMinecarts();

        SoundHelper.playSound(gameHandle.getServer(), SoundEvents.ENTITY_IRON_GOLEM_HURT, SoundCategory.HOSTILE, 0.9f, 0f);

        Translations translations = gameHandle.getTranslations();
        Participants participants = gameHandle.getParticipants();

        for (ServerPlayerEntity player : participants) {
            var msg = Text.literal("⚠ ")
                    .append(translations.translateText(player, "game.ap2.musical_minecart.deadline")
                            .styled(s -> s.withColor(0xff0000).withBold(true)))
                    .append(" ⚠").withColor(0xffff00);

            player.sendMessage(msg, true);
        }

        taskHandle = gameHandle.getGameScheduler().timeout(this::eliminatePlayers, ELIMINATION_DELAY_TICKS);
    }

    private void spawnMinecarts() {
        int count = gameHandle.getParticipants().count() - 1;
        var pos = new BlockPos.Mutable();

        ServerWorld world = getWorld();

        for (int i = 0; i < count; i++) {
            bounds.randomBlockPos(pos, random);

            MinecartEntity minecart = new MinecartEntity(EntityType.MINECART, world);
            minecart.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            minecart.setInvulnerable(true);

            world.spawnEntity(minecart);
            minecartEntities.add(minecart);
        }
    }

    private void removeMinecarts() {
        minecartEntities.forEach(Entity::discard);
        minecartEntities.clear();
    }

    private void eliminatePlayers() {
        ServerWorld world = getWorld();
        Participants participants = gameHandle.getParticipants();

        Set<ServerPlayerEntity> toEliminate = new HashSet<>();

        for (ServerPlayerEntity player : participants) {
            if (player.getVehicle() instanceof MinecartEntity) continue;

            double x = player.getX(), y = player.getY(), z = player.getZ();

            world.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1f, 0f);
            world.spawnParticles(ParticleTypes.LAVA, x, y, z, 100, 0.5, 0.5, 0.5, 0.2);

            toEliminate.add(player);
        }

        eliminateAll(toEliminate);

        if (winManager.isGameOver() || participants.count() == 0) return;

        int passDelay = Math.max(0, NEXT_SONG_DELAY_TICKS - Ticks.seconds(1));

        TaskScheduler scheduler = gameHandle.getGameScheduler();

        scheduler.timeout(() -> {
            for (ServerPlayerEntity player : participants) {
                player.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.NEUTRAL, 0.5f, 1f);
            }
        }, passDelay);

        scheduler.timeout(this::nextSong, NEXT_SONG_DELAY_TICKS);
    }

    private void tickParticle() {
        if (particleBox == null || songHandle == null) return;

        ServerWorld world = getWorld();

        for (int i = 0; i < PARTICLE_AMOUNT; i++) {
            Vec3d pos = particleBox.randomPos(random);

            world.spawnParticles(ParticleTypes.NOTE, pos.getX(), pos.getY(), pos.getZ(), 10,
                    3, 2, 3, 1);
        }
    }

    @Override
    public int getMaxDurationTicks() {
        return DEBUG_INFINITE_SONGS ? -1 : super.getMaxDurationTicks();
    }

    private synchronized void skipSong() {
        if (!ready.get()) return;

        if (songHandle != null) {
            songHandle.stop();
            songHandle = null;
        }

        if (taskHandle != null) {
            taskHandle.cancel();
        }

        if (timer != null) {
            timer.stop();
        }

        removeMinecarts();
        nextSong();
    }
}
