package work.lclpnet.ap2.api.util.music;

import net.minecraft.util.Identifier;

import java.util.Set;

public interface SongManager {

    Set<LoadableSong> getSongs(Identifier tag);
}
