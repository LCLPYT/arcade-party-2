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
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.util.music.ConfiguredSong;
import work.lclpnet.ap2.api.util.music.SongManager;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.SoundHelper;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.notica.Notica;
import work.lclpnet.notica.api.CheckedSong;
import work.lclpnet.notica.api.SongHandle;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static net.minecraft.util.Formatting.GREEN;

public class MusicalMinecartInstance extends EliminationGameInstance {

    private static final int
            MIN_DELAY_TICKS = Ticks.seconds(10),
            MAX_DELAY_TICKS = Ticks.seconds(20),
            ELIMINATION_DELAY_TICKS = Ticks.seconds(9),
            NEXT_SONG_DELAY_TICKS = Ticks.seconds(2);
    private final MMSongs songManager;
    private final Random random = new Random();
    private BlockBox bounds = null;
    @Nullable
    private SongHandle songHandle = null;
    private final Set<MinecartEntity> minecartEntities = new HashSet<>();

    public MusicalMinecartInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        SongManager songManager = gameHandle.getSongManager();
        TranslationService translations = gameHandle.getTranslations();

        this.songManager = new MMSongs(songManager, translations, random, gameHandle.getLogger());
    }

    @Override
    protected void prepare() {
        bounds = MapUtil.readBox(getMap().requireProperty("bounds"));
        songManager.init();

        useRemainingPlayersDisplay();
    }

    @Override
    protected void ready() {
        nextSong();
    }

    private void nextSong() {
        removeMinecarts();

        songManager.getNextSong().thenAccept(this::playSong).whenComplete((res, err) -> {
            if (err == null) return;

            gameHandle.getLogger().error("Failed to load next song", err);
        });
    }

    private void playSong(ConfiguredSong config) {
        MinecraftServer server = gameHandle.getServer();

        var players = PlayerLookup.all(server);

        CheckedSong song = config.song();

        Notica notica = Notica.getInstance(server);
        songHandle = notica.playSong(song, config.volume(), config.startTick(), players);

        long delay = MIN_DELAY_TICKS + random.nextInt(MAX_DELAY_TICKS - MIN_DELAY_TICKS + 1);
        gameHandle.getGameScheduler().timeout(this::stopMusic, delay);

        for (ServerPlayerEntity player : players) {
            Text title = songManager.getSongTitle(player, config.info(), song.song().metaData());

            if (title == null) return;  // can return here, as the title is only null when the song has no name

            Text msg = Text.literal("🎵 ").append(title).formatted(GREEN);
            player.sendMessage(msg, true);
        }
    }

    private void stopMusic() {
        if (songHandle != null) {
            songHandle.stop();
            songHandle = null;
        }

        spawnMinecarts();

        SoundHelper.playSound(gameHandle.getServer(), SoundEvents.ENTITY_IRON_GOLEM_HURT, SoundCategory.HOSTILE, 0.9f, 0f);

        TranslationService translations = gameHandle.getTranslations();
        Participants participants = gameHandle.getParticipants();

        for (ServerPlayerEntity player : participants) {
            var msg = Text.literal("⚠ ")
                    .append(translations.translateText(player, "game.ap2.musical_minecart.deadline")
                            .styled(s -> s.withColor(0xff0000).withBold(true)))
                    .append(" ⚠").withColor(0xffff00);

            player.sendMessage(msg, true);
        }

        gameHandle.getGameScheduler().timeout(this::eliminatePlayers, ELIMINATION_DELAY_TICKS);
    }

    private void spawnMinecarts() {
        int count = gameHandle.getParticipants().count() - 1;
        var pos = new BlockPos.Mutable();

        ServerWorld world = getWorld();

        for (int i = 0; i < count; i++) {
            bounds.getRandomPosition(pos, random);

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

        for (ServerPlayerEntity player : participants) {
            if (player.getVehicle() instanceof MinecartEntity) continue;

            double x = player.getX(), y = player.getY(), z = player.getZ();

            world.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1f, 0f);
            world.spawnParticles(ParticleTypes.LAVA, x, y, z, 100, 0.5, 0.5, 0.5, 0.2);

            eliminate(player);
        }

        if (winManager.isGameOver() || participants.count() == 0) return;

        int passDelay = Math.max(0, NEXT_SONG_DELAY_TICKS - Ticks.seconds(1));

        TaskScheduler scheduler = gameHandle.getGameScheduler();

        scheduler.timeout(() -> {
            for (ServerPlayerEntity player : participants) {
                player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.NEUTRAL, 0.5f, 1f);
            }
        }, passDelay);

        scheduler.timeout(this::nextSong, NEXT_SONG_DELAY_TICKS);
    }
}
