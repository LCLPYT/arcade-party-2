package work.lclpnet.ap2.game.maze_scape.util;

import java.util.*;
import java.util.function.Supplier;

/**
 * A generator that provides random elements out of a specified source collection.
 * Supplied elements are chosen without duplicates until source.size() elements were supplied.
 * After that, the pool is reset and elements can be chosen again, until source.size() elements are supplied again.
 * <br>
 * Not thread safe by itself.
 * @param <T> The element type.
 */
public class RandomGenerator<T> implements Supplier<T> {

    private final Collection<T> source;
    private final Random random;
    private final List<T> pool;

    /**
     * Create a new RandomGenerator.
     * @param source The source collection.
     * @param random The RNG.
     */
    public RandomGenerator(Collection<T> source, Random random) {
        this.source = Collections.unmodifiableCollection(source);
        this.random = random;
        this.pool = new ArrayList<>(source.size());
    }

    @Override
    public T get() {
        if (pool.isEmpty()) {
            pool.addAll(source);
        }

        return pool.remove(random.nextInt(pool.size()));
    }

    public Collection<T> source() {
        return source;
    }
}
