package work.lclpnet.ap2.impl.util.music;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.util.music.SongCache;
import work.lclpnet.notica.api.CheckedSong;

/**
 * A {@link SongCache} that never caches any entries.
 */
public final class VoidSongCache implements SongCache {

    public static final VoidSongCache INSTANCE = new VoidSongCache();

    private VoidSongCache() {}

    @Override
    public @Nullable CheckedSong getCachedSong(Object key) {
        return null;
    }

    @Override
    public void cacheSong(Object key, @NotNull CheckedSong song) {

    }
}
