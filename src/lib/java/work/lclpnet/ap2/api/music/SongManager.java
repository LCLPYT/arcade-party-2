package work.lclpnet.ap2.api.music;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface SongManager {

    CompletableFuture<Set<WeightedSong>> getSongs(ResourceLocation tag);

    CompletableFuture<Optional<WeightedSong>> getSong(ResourceLocation tag, String songName);

    void cache(WeightedSong song, ResourceLocation tag, String songName);

    default CompletableFuture<Optional<WeightedSong>> getSongAndCache(ResourceLocation tag, String songName) {
        return getSong(tag, songName).thenApply(optSong -> {
            optSong.ifPresent(song -> cache(song, tag, songName));

            return optSong;
        });
    }
}
