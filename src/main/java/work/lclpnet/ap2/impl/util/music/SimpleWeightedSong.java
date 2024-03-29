package work.lclpnet.ap2.impl.util.music;

import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.util.music.LoadableSong;
import work.lclpnet.ap2.api.util.music.WeightedSong;
import work.lclpnet.ap2.impl.util.WeightedList;

import java.util.Collections;
import java.util.Random;
import java.util.Set;

public class SimpleWeightedSong implements WeightedSong {

    private final Set<LoadableSong> songs;
    private final WeightedList<LoadableSong> weightedSongs;

    public SimpleWeightedSong(Set<LoadableSong> songs) {
        if (songs.isEmpty()) {
            throw new IllegalArgumentException("Songs cannot be empty");
        }

        this.songs = Collections.unmodifiableSet(songs);
        this.weightedSongs = new WeightedList<>();

        for (LoadableSong song : this.songs) {
            this.weightedSongs.add(song, song.getWeight());
        }
    }

    @NotNull
    @Override
    public LoadableSong getRandomElement(Random random) {
        LoadableSong elem = weightedSongs.getRandomElement(random);

        assert elem != null;  // cannot be null, if size > 0

        return elem;
    }

    @Override
    public Set<LoadableSong> getAllElements() {
        return songs;
    }
}
