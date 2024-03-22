package work.lclpnet.ap2.impl.util.music;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.util.music.SongCache;
import work.lclpnet.notica.api.CheckedSong;

import java.util.HashMap;
import java.util.Map;

public class MapSongCache implements SongCache {

    private final Map<Object, CheckedSong> cache = new HashMap<>();

    @Override
    public @Nullable CheckedSong getCachedSong(Object key) {
        return cache.get(key);
    }

    @Override
    public void cacheSong(Object key, @NotNull CheckedSong song) {
        cache.put(key, song);
    }
}
