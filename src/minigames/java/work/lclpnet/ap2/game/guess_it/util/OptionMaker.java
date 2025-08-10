package work.lclpnet.ap2.game.guess_it.util;

import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.util.collection.IndexedIterable;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.IntFunction;

public class OptionMaker {

    private OptionMaker() {}

    public static <T> List<T> createOptions(Set<T> pool, int optionCount, Random random) {
        return createOptions(List.copyOf(pool), optionCount, random);
    }

    public static <T> List<T> createOptions(List<@NotNull T> pool, int optionCount, Random random) {
        return createOptions(pool.size(), optionCount, random, pool::get);
    }

    public static <T> List<T> createOptions(IndexedIterable<T> pool, int optionCount, Random random) {
        return createOptions(pool.size(), optionCount, random, pool::getOrThrow);
    }

    private static <T> List<T> createOptions(int poolSize, int optionCount, Random random, IntFunction<T> indexFunction) {
        if (poolSize < optionCount) {
            throw new IllegalStateException("There are less options available than requested");
        }

        IntSet indices = new IntArraySet(optionCount);

        while (indices.size() < optionCount) {
            int i = random.nextInt(poolSize);
            indices.add(i);
        }

        return indices.intStream()
                .mapToObj(indexFunction)
                .toList();
    }
}
