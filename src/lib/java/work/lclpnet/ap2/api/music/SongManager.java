package work.lclpnet.ap2.api.music;

import net.minecraft.util.Identifier;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface SongManager {

    CompletableFuture<Set<WeightedSong>> getSongs(Identifier tag);

    CompletableFuture<Optional<WeightedSong>> getSong(Identifier tag, String songName);

    void cache(WeightedSong song, Identifier tag, String songName);

    default CompletableFuture<Optional<WeightedSong>> getSongAndCache(Identifier tag, String songName) {
        return getSong(tag, songName).thenApply(optSong -> {
            optSong.ifPresent(song -> cache(song, tag, songName));

            return optSong;
        });
    }
}
