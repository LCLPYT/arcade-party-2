package work.lclpnet.ap2.api.util.music;

import net.minecraft.util.Identifier;

import java.util.Optional;
import java.util.Set;

public interface SongManager {

    Set<WeightedSong> getSongs(Identifier tag);

    Optional<WeightedSong> getSong(Identifier tag, Identifier id);
}
