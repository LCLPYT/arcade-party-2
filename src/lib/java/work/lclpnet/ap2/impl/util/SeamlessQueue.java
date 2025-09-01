package work.lclpnet.ap2.impl.util;

import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.impl.ds.IndexedSet;

import java.util.*;

import static java.lang.Math.*;

public class SeamlessQueue<T> {

    private final Set<T> pool;
    private final Random random;
    private final int margin;
    private final List<T> queue = new ArrayList<>();
    private final SequencedCollection<T> history = new LinkedHashSet<>();

    /**
     * Creates a new seamless queue.
     * @param pool The base element pool. Must not be empty.
     * @param random The RNG.
     * @param margin The minimum number of elements before an element is repeated.
     *               Must be less than the size of the base element pool.
     * @param lastElements Optional information about the last elements, restored from a past instance, in reverse order.
     */
    public SeamlessQueue(Set<T> pool, Random random, int margin, List<T> lastElements) {
        if (pool.isEmpty()) throw new IllegalArgumentException("Pool is empty");
        if (margin >= pool.size()) throw new IllegalArgumentException("Element margin must less than the size of the " +
                "element pool (is %s but most be at most %s)".formatted(margin, pool.size() - 1));

        this.pool = Set.copyOf(pool);
        this.random = random;
        this.margin = max(0, margin);

        pushHistory(lastElements);
    }

    @NotNull
    public synchronized T next() {
        ensureAtLeast(1);

        return queue.removeFirst();
    }

    public synchronized List<T> peek(int amount) {
        ensureAtLeast(amount);

        return List.copyOf(queue.subList(0, amount));
    }

    private void ensureAtLeast(int elements) {
        if (queue.size() >= elements) return;

        int cycles = (int) ceil((double) elements / pool.size());

        for (int i = 0; i < cycles; i++) {
            appendCycle();
        }
    }

    private void appendCycle() {
        // append whole cycle of the pool, so that every element is guaranteed to occur once before the next cycle
        IndexedSet<T> cycleRemaining = new IndexedSet<>(pool);
        final int amount = cycleRemaining.size();
        List<T> candidates = new ArrayList<>(amount);

        for (int i = 0; i < amount; i++) {
            // find all candidates respecting margin
            candidates.clear();
            candidates.addAll(cycleRemaining);
            candidates.removeIf(history::contains);  // history.size() <= margin

            T elem = candidates.get(random.nextInt(candidates.size()));
            cycleRemaining.remove(elem);

            queue.add(elem);
            pushHistory(elem);
        }
    }

    /**
     * Pushes the given elements onto the history.
     * @param elements The elements to add, in reverse order. The first element is the last occurring, the last element is the first occurring.
     */
    private void pushHistory(List<T> elements) {
        history.addAll(elements.subList(0, min(margin, elements.size())));
        trimHistory();
    }

    private void pushHistory(T element) {
        history.add(element);

        trimHistory();
    }

    private void trimHistory() {
        int overhead = max(0, history.size() - margin);

        for (int i = 0; i < overhead; i++) {
            history.removeFirst();
        }
    }
}
