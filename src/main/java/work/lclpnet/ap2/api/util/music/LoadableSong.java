package work.lclpnet.ap2.api.util.music;

import net.minecraft.util.Identifier;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;

public interface LoadableSong {

    /**
     * Loads the song synchronously.
     * @return The checked song.
     */
    CompletableFuture<ConfiguredSong> load(SongCache cache, Logger logger);

    Identifier getId();

    float getWeight();

    SongInfo getInfo();
}
