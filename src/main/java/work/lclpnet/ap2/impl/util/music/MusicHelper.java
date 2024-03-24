package work.lclpnet.ap2.impl.util.music;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.util.music.LoadableSong;
import work.lclpnet.ap2.api.util.music.SongCache;
import work.lclpnet.ap2.api.util.music.SongWrapper;
import work.lclpnet.ap2.api.util.music.WeightedSong;
import work.lclpnet.notica.Notica;
import work.lclpnet.notica.api.SongHandle;

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
                .thenAccept(config -> {
                    Notica notica = Notica.getInstance(server);

                    float scaledVolume = volume * config.volume();
                    SongHandle handle = notica.playSong(config.song(), scaledVolume, config.startTick(), players);

                    wrapper.setHandle(handle);
                })
                .whenComplete((res, err) -> {
                    if (err == null) return;

                    logger.error("Failed to load song {}", loadable.getId());
                });

        return wrapper;
    }
}
