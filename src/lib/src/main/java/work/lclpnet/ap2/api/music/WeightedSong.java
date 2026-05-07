package work.lclpnet.ap2.api.music;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.Random;
import java.util.Set;

public interface WeightedSong {

    @NotNull
    LoadableSong getRandomElement(Random random);

    Set<LoadableSong> getAllElements();

    Identifier getSongId();
}
