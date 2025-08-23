package work.lclpnet.ap2.impl.music;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.music.*;
import work.lclpnet.notica.Notica;
import work.lclpnet.notica.api.PlaybackOptions;
import work.lclpnet.notica.api.PlaybackVariant;
import work.lclpnet.notica.api.SongHandle;
import work.lclpnet.notica.api.StereoMode;

import java.util.Collection;
import java.util.Random;

public class MusicHelper {

    private static final Random random = new Random();

    private MusicHelper() {}

    public static SongWrapper playSong(WeightedSong song, float volume, Collection<? extends ServerPlayerEntity> players,
                                       MinecraftServer server, SongCache cache, Logger logger) {
        return playSong(song, volume, players, server, cache, logger, random);
    }

    public static SongWrapper playSong(WeightedSong song, float volume, Collection<? extends ServerPlayerEntity> players,
                                       MinecraftServer server, SongCache cache, Logger logger, Random random) {

        SongWrapperImpl wrapper = new SongWrapperImpl();
        LoadableSong loadable = song.getRandomElement(random);

        loadable.load(cache, logger)
                .thenAccept(config -> playSong(wrapper, config, volume, config.info().meta().startTick().orElse(0), players, server))
                .whenComplete((res, err) -> {
                    if (err == null) return;

                    logger.error("Failed to load song {}", loadable.getId(), err);
                });

        return wrapper;
    }

    public static SongWrapper playSong(ConfiguredSong song, float volume, int startTick, MinecraftServer server) {
        var wrapper = new SongWrapperImpl();

        playSong(wrapper, song, volume, startTick, PlayerLookup.all(server), server);

        return wrapper;
    }

    private static void playSong(SongWrapperImpl wrapper, ConfiguredSong song, float volume, int startTick,
                                 Collection<? extends ServerPlayerEntity> players, MinecraftServer server) {

        Notica notica = Notica.getInstance(server);

        SongInfo.Meta meta = song.info().meta();

        float finalVolume = meta.volume().orElse(1f) * volume;
        StereoMode stereoMode = meta.stereoMode().orElse(StereoMode.SPATIAL);

        var playbackOptions = new PlaybackOptions(finalVolume, PlaybackVariant.STREAMED, stereoMode);

        SongHandle handle = notica.playSong(song.checkedSong(), playbackOptions, startTick, players);

        wrapper.setHandle(handle);
    }
}
