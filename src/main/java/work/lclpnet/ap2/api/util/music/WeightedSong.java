package work.lclpnet.ap2.api.util.music;

import java.util.Random;
import java.util.Set;

public interface WeightedSong {

    LoadableSong getRandomElement(Random random);

    Set<LoadableSong> getAllElements();
}
