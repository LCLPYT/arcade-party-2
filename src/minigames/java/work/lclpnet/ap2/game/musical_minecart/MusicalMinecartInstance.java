package work.lclpnet.ap2.game.musical_minecart;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.FrogEntity;
import net.minecraft.entity.passive.FrogVariant;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.api.util.music.ConfiguredSong;
import work.lclpnet.ap2.api.util.music.SongInfo;
import work.lclpnet.ap2.api.util.music.SongManager;
import work.lclpnet.ap2.core.type.ApVariantHolder;
import work.lclpnet.ap2.game.musical_minecart.cmd.SetSongCommand;
import work.lclpnet.ap2.game.musical_minecart.cmd.SkipSongCommand;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.Hints;
import work.lclpnet.ap2.impl.util.SoundHelper;
import work.lclpnet.kibu.cmd.type.CommandRegistrar;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.EntityDismountCallback;
import work.lclpnet.kibu.hook.entity.EntityMountCallback;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskHandle;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.TextTranslatable;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.util.BossBarTimer;
import work.lclpnet.notica.Notica;
import work.lclpnet.notica.api.*;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.Math.*;
import static net.minecraft.util.Formatting.GREEN;
import static work.lclpnet.ap2.impl.util.TranslationUtil.transformText;

public class MusicalMinecartInstance extends EliminationGameInstance implements MapBootstrap {

    private static final boolean
            DEBUG_INFINITE_SONGS = false,
            DEBUG_FULL_DELAY = false,
            DEBUG_INFO = false;

    private static final int
            MIN_DELAY_TICKS = Ticks.seconds(10),
            MAX_DELAY_TICKS = Ticks.seconds(20),  // do not increase; song timings are adjusted to max 20
            NEXT_SONG_DELAY_TICKS = Ticks.seconds(2),
            PARTICLE_AMOUNT = 2,
            MAX_DECOYS = 4;

    private static final float
            MUSIC_VOLUME = 0.75f,
            DECOY_CHANCE = 0.15f;

    private final MMSongs songManager;
    private final Random random = new Random();
    private final Set<MinecartEntity> minecartEntities = new HashSet<>();
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private boolean intermission = false;  // intermission is true if a priority song inhibited the next queue song
    private BlockBox bounds = null, particleBox = null;
    @Nullable
    private SongHandle songHandle = null;
    private BossBarTimer timer = null;
    private List<TaskHandle> taskHandles = List.of();
    private CompletableFuture<ConfiguredSong> nextSong = null;
    private int eliminationDelayTicks = Ticks.seconds(9);
    private boolean minecartsGlowing = false;

    public MusicalMinecartInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        SongManager songManager = gameHandle.getSongManager();
        Translations translations = gameHandle.getTranslations();

        this.songManager = new MMSongs(songManager, translations, random, gameHandle.getLogger());
    }

    @Override
    public CompletableFuture<Void> createWorldBootstrap(ServerWorld world, GameMap map) {
        return songManager.init();
    }

    @Override
    protected void prepare() {
        GameMap map = getMap();

        bounds = MapUtil.readBox(map.requireProperty("bounds"));

        if (map.hasProperty("particle", JSONArray.class)) {
            particleBox = MapUtil.readBox(map.requireProperty("particle"));
        }

        var eliminationDelayTicks = map.getProperty("elimination-delay-ticks");

        if (eliminationDelayTicks instanceof Number n) {
            this.eliminationDelayTicks = max(0, n.intValue());
        }


        useRemainingPlayersDisplay();

        gameHandle.protect(config -> config.allow(ProtectionTypes.MOUNT));

        CommandRegistrar commands = gameHandle.getCommandRegistrar();
        new SetSongCommand(songManager, this::skipSong).register(commands);
        new SkipSongCommand(this::skipSong).register(commands);

        gameHandle.getGameScheduler().timeout(() -> {
            var hints = new Hints(gameHandle.getTranslations(), gameHandle.getServer());
            hints.sendModHint(Hints.Mod.NOTICA);
        }, max(0, getInitialDelay() - Ticks.seconds(3)));
    }

    @Override
    protected void ready() {
        nextSong();

        gameHandle.getGameScheduler().interval(this::tickParticle, 7);

        ready.set(true);

        HookRegistrar hooks = gameHandle.getHookRegistrar();

        hooks.registerHook(EntityMountCallback.HOOK, (entity, vehicle, force) -> {
            if (vehicle.isGlowing()) {
                vehicle.setGlowing(false);
            }

            return false;
        });

        hooks.registerHook(EntityDismountCallback.HOOK, (entity, vehicle) -> {
            if (minecartsGlowing && vehicle instanceof MinecartEntity) {
                vehicle.setGlowing(true);
            }

            return false;
        });
    }

    private synchronized void nextSong() {
        removeMinecarts();

        intermission = false;

        var future = nextSong;

        if (songManager.hasPrioritySongs()) {
            // do not replace existing nextSong future
            future = loadNextSong();
            intermission = nextSong != null;
        } else if (future == null) {
            future = nextSong = loadNextSong();
        }

        future.whenComplete((res, err) -> {
            if (err == null) {
                this.playSong(res);
                return;
            }

            gameHandle.getLogger().error("Failed to load next song", err);

            getWinManagerAccess().draw();
        });
    }

    private synchronized void playSong(ConfiguredSong config) {
        if (!intermission) {
            nextSong = loadNextSong();
        }

        MinecraftServer server = gameHandle.getServer();

        var players = PlayerLookup.all(server);

        CheckedSong song = config.song();
        SongInfo.Meta meta = config.info().meta();
        var playbackOptions = new PlaybackOptions(meta.volume().orElse(1f) * MUSIC_VOLUME, PlaybackVariant.STREAMED, meta.stereoMode().orElse(StereoMode.SPATIAL));

        Notica notica = Notica.getInstance(server);
        songHandle = notica.playSong(song, playbackOptions, meta.startTick().orElse(0), players);

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

        taskHandles = List.of(gameHandle.getGameScheduler().timeout(this::stopMusic, delay));

        TextTranslatable title = songManager.getSongTitle(config.info(), song.song().metaData());

        if (title == null) return;

        Translations translations = gameHandle.getTranslations();

        transformText(
                translations.translateText("game.ap2.musical_minecart.now_playing", title),
                text -> Text.literal("🎵 ").append(text).formatted(GREEN),
                translations
        ).sendTo(players);
    }

    private synchronized CompletableFuture<ConfiguredSong> loadNextSong() {
        return songManager.getNextSong();
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

        TaskScheduler scheduler = gameHandle.getGameScheduler();

        taskHandles = List.of(
                scheduler.timeout(this::markFreeMinecarts, eliminationDelayTicks / 2),
                scheduler.timeout(this::eliminatePlayers, eliminationDelayTicks)
        );
    }

    private void spawnMinecarts() {
        double p = random.nextDouble();
        int decoys = (int) floor(log(p) / log(DECOY_CHANCE));

        decoys = max(0, min(MAX_DECOYS, decoys));

        int count = gameHandle.getParticipants().count() - 1;

        if (count <= 0) {
            decoys = 0;
        }

        int total = count + decoys;
        var pos = new BlockPos.Mutable();

        ServerWorld world = getWorld();

        for (int i = 0; i < total; i++) {
            bounds.randomBlockPos(pos, random);

            var minecart = new MinecartEntity(EntityType.MINECART, world);
            minecart.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            minecart.setInvulnerable(true);

            world.spawnEntity(minecart);
            minecartEntities.add(minecart);

            if (i >= count) {
                createDecoyEntity(world, minecart);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void createDecoyEntity(ServerWorld world, MinecartEntity minecart) {
        var frog = new FrogEntity(EntityType.FROG, world);
        frog.setPosition(minecart.getPos());

        var frogTypes = world.getRegistryManager().getOrThrow(RegistryKeys.FROG_VARIANT).getIndexedEntries();

        if (frogTypes.size() <= 0) return;

        var variant = frogTypes.get(random.nextInt(frogTypes.size()));

        if (variant != null) {
            ((ApVariantHolder<RegistryEntry<FrogVariant>>) frog).ap2$setVariant(variant);
        }

        world.spawnEntity(frog);
        frog.startRiding(minecart);
    }

    private void markFreeMinecarts() {
        minecartsGlowing = true;

        for (MinecartEntity minecart : minecartEntities) {
            if (minecart.getPassengerList().isEmpty()) {
                minecart.setGlowing(true);
            }
        }
    }

    private void removeMinecarts() {
        for (MinecartEntity minecart : minecartEntities) {
            // remove all non-player passengers
            minecart.getPassengerList().stream()
                    .filter(entity -> !(entity instanceof ServerPlayerEntity))
                    .forEach(Entity::discard);

            minecart.discard();
        }

        minecartEntities.clear();
        minecartsGlowing = false;
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

        int passDelay = max(0, NEXT_SONG_DELAY_TICKS - Ticks.seconds(1));

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

        if (taskHandles != null) {
            taskHandles.forEach(TaskHandle::cancel);
        }

        if (timer != null) {
            timer.stop();
        }

        removeMinecarts();
        nextSong();
    }
}
