package work.lclpnet.ap2.game.musical_minecart;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.util.music.ConfiguredSong;
import work.lclpnet.ap2.api.util.music.LoadableSong;
import work.lclpnet.ap2.api.util.music.SongCache;
import work.lclpnet.ap2.api.util.music.SongManager;
import work.lclpnet.ap2.base.ArcadeParty;
import work.lclpnet.ap2.impl.util.WeightedList;
import work.lclpnet.ap2.impl.util.music.MapSongCache;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class MMSongs {

    private static final Identifier MUSICAL_MINECART_TAG = ArcadeParty.identifier("musical_minecart");
    private final SongManager songManager;
    private final Random random;
    private final Logger logger;
    private final Set<WeightedList<LoadableSong>> songs = new HashSet<>();
    private final List<WeightedList<LoadableSong>> queue = new ArrayList<>();
    private final SongCache cache = new MapSongCache();

    public MMSongs(SongManager songManager, Random random, Logger logger) {
        this.songManager = songManager;
        this.random = random;
        this.logger = logger;
    }

    public void init() {
        Set<LoadableSong> loadableSongs = songManager.getSongs(MUSICAL_MINECART_TAG);
        Multimap<Identifier, LoadableSong> byActualSong = ArrayListMultimap.create();

        // group by id
        for (LoadableSong loadable : loadableSongs) {
            byActualSong.put(loadable.getId(), loadable);
        }

        for (Identifier songId : byActualSong.keySet()) {
            var configuredSongs = byActualSong.get(songId);

            if (configuredSongs.isEmpty()) continue;

            WeightedList<LoadableSong> weighted = new WeightedList<>();

            for (LoadableSong loadable : configuredSongs) {
                weighted.add(loadable, loadable.getWeight());
            }

            songs.add(weighted);
        }
    }

    public CompletableFuture<ConfiguredSong> getNextSong() {
        if (queue.isEmpty()) {
            populateQueue();
        }

        var weightedSong = queue.remove(0);
        LoadableSong loadable = weightedSong.getRandomElement(random);

        if (loadable == null) {
            return CompletableFuture.failedFuture(new NoSuchElementException("Failed to get random song"));
        }

        return loadable.load(cache, logger);
    }

    private void populateQueue() {
        if (songs.isEmpty()) throw new IllegalStateException("There are no songs defined");

        queue.clear();
        queue.addAll(songs);
        Collections.shuffle(queue, random);
    }
}
