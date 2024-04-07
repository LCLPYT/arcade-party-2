package work.lclpnet.ap2.game.guess_it.util;

import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.util.List;
import java.util.Random;
import java.util.Set;

public class OptionMaker {

    private OptionMaker() {}

    public static <T> List<T> createOptions(Set<T> pool, int optionCount, Random random) {
        var index = List.copyOf(pool);
        int soundCount = index.size();

        if (soundCount < optionCount) {
            throw new IllegalStateException("There are less options available than requested");
        }

        IntSet indices = new IntArraySet(optionCount);

        while (indices.size() < optionCount) {
            int i = random.nextInt(soundCount);
            indices.add(i);
        }

        return indices.intStream()
                .mapToObj(index::get)
                .toList();
    }
}
