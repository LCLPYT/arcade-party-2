package work.lclpnet.ap2.impl.util.music;

import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.util.music.ConfiguredSong;
import work.lclpnet.ap2.api.util.music.LoadableSong;
import work.lclpnet.ap2.api.util.music.SongCache;
import work.lclpnet.notica.api.CheckedSong;
import work.lclpnet.notica.util.ServerSongLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class PathLoadableSong implements LoadableSong {

    private final Path path;
    private final Identifier id;
    private final float volume;
    private final int startTick;
    private final float weight;

    public PathLoadableSong(Path path, Identifier id, float volume, int startTick, float weight) {
        this.path = path;
        this.id = id;
        this.volume = volume;
        this.startTick = startTick;
        this.weight = weight;
    }

    @Override
    public CompletableFuture<ConfiguredSong> load(SongCache cache, Logger logger) {
        CheckedSong cached = cache.getCachedSong(path);

        if (cached != null) {
            return CompletableFuture.completedFuture(new ConfiguredSong(cached, volume, startTick, weight));
        }

        return CompletableFuture.supplyAsync(() -> {
            CheckedSong song;

            try (var in = Files.newInputStream(path)) {
                song = ServerSongLoader.load(in, id);
            } catch (IOException e) {
                logger.error("Failed to load song {}", id, e);
                return null;
            }

            cache.cacheSong(path, song);

            return new ConfiguredSong(song, volume, startTick, weight);
        });
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public float getWeight() {
        return weight;
    }
}
