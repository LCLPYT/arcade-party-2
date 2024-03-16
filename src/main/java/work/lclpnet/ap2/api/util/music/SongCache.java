package work.lclpnet.ap2.api.util.music;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.notica.api.CheckedSong;

public interface SongCache {

    @Nullable
    CheckedSong getCachedSong(Object key);

    void cacheSong(Object key, @NotNull CheckedSong song);
}
