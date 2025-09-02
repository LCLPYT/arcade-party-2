package work.lclpnet.ap2.impl.util;

import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.util.QueueTransfer;

import java.util.*;
import java.util.function.Predicate;

import static java.lang.Math.max;

public class SeamlessQueue<T> {

    private final Set<T> basePool;
    private final Set<T> filteredPool;
    private final Random random;
    private final List<T> queue = new ArrayList<>();
    private final History<T> realHistory;
    private final History<T> futureHistory;
    private final Set<T> realOccurred = new LinkedHashSet<>();
    private final Set<T> futureOccurred = new HashSet<>();

    /**
     * Creates a new seamless queue.
     * @param pool The base element pool. Must not be empty.
     * @param random The RNG.
     * @param margin The minimum number of elements before an element is repeated.
     *               Must be less than the size of the base element pool.
     * @param transfer Optional transfer information of a previous queue, e.g. restored from a past instance.
     *
     */
    public SeamlessQueue(Set<T> pool, Random random, int margin, QueueTransfer<T> transfer) {
        if (pool.isEmpty()) throw new IllegalArgumentException("Pool is empty");

        if (margin >= pool.size()) throw new IllegalArgumentException("Element margin must less than the size of the " +
                "element pool (is %s but most be at most %s)".formatted(margin, pool.size() - 1));

        if (margin < 0) throw new IllegalArgumentException("Margin must not be negative");

        this.basePool = Set.copyOf(pool);
        this.filteredPool = new HashSet<>(basePool);
        this.random = random;

        this.realHistory = new History<>(margin);
        this.futureHistory = new History<>(margin);

        realHistory.push(transfer.history());
        futureHistory.push(transfer.history());

        realOccurred.addAll(transfer.occurred());
        futureOccurred.addAll(transfer.occurred());

        checkDepleted();
    }

    private void checkDepleted() {
        if (realOccurred.size() >= basePool.size()) {
            realOccurred.clear();
        }
    }

    @NotNull
    public synchronized T next() {
        ensureAtLeast(1);

        return queue.removeFirst();
    }

    public synchronized void pushElement(T element) {
        realHistory.push(element);
        realOccurred.add(element);

        checkDepleted();
    }
    
    public synchronized void pushUpcoming(T element) {
        futureHistory.push(element);
    }

    public synchronized List<T> peek(int amount) {
        ensureAtLeast(amount);

        return List.copyOf(queue.subList(0, amount));
    }

    private void ensureAtLeast(int elements) {
        if (queue.size() >= elements) return;

        int missing = elements - queue.size();
        List<T> candidates = new ArrayList<>(filteredPool.size());

        for (int i = 0; i < missing; i++) {
            candidates.clear();
            
            remainingElements(candidates);
            
            // find all candidates respecting margin
            candidates.removeIf(futureHistory::contains);  // history.size() <= margin

            T elem = candidates.get(random.nextInt(candidates.size()));
            futureOccurred.add(elem);

            queue.add(elem);
            futureHistory.push(elem);
        }
    }
    
    private void remainingElements(List<T> dst) {
        for (T elem : filteredPool) {
            if (!futureOccurred.contains(elem)) {
                dst.add(elem);
            }
        }

        if (!dst.isEmpty()) return;
        
        // reset cycle
        futureOccurred.clear();
        dst.addAll(filteredPool);
    }

    public void filter(Predicate<T> filter) {
        filteredPool.clear();
        basePool.stream().filter(filter).forEach(filteredPool::add);

        var invalid = Predicate.not(filter);

        queue.removeIf(invalid);
        futureHistory.sequence.removeIf(invalid);
    }

    public QueueTransfer<T> transfer() {
        return new QueueTransfer<>(realHistory.copySequence(), realOccurred);
    }

    private static class History<T> {

        private final SequencedCollection<T> sequence;
        private final int maxSize;

        public History(int maxSize) {
            this.maxSize = maxSize;
            sequence = new LinkedHashSet<>(maxSize);
        }

        public synchronized void push(List<T> elements) {
            int end = elements.size();
            int start = max(0, end - maxSize);

            sequence.addAll(elements.subList(start, end));

            trim();
        }

        public synchronized void push(T element) {
            sequence.add(element);

            trim();
        }

        private void trim() {
            int overhead = max(0, sequence.size() - maxSize);

            for (int i = 0; i < overhead; i++) {
                sequence.removeFirst();
            }
        }

        public boolean contains(T elem) {
            return sequence.contains(elem);
        }

        public List<T> copySequence() {
            return List.copyOf(sequence);
        }
    }
}
