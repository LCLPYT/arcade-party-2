package work.lclpnet.ap2.impl.ds;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class MinHeap<T> extends AbstractQueue<T> {

    private final @Nullable Comparator<T> comparator;
    private final ArrayList<T> heap;
    private final Object2IntMap<T> index;

    public MinHeap() {
        this(null);
    }

    public MinHeap(@Nullable Comparator<T> comparator) {
        this.comparator = comparator;

        heap = new ArrayList<>();
        index = new Object2IntOpenHashMap<>();
        index.defaultReturnValue(-1);
    }

    @Override
    public @NotNull Iterator<T> iterator() {
        return heap.iterator();
    }

    @Override
    public int size() {
        return heap.size();
    }

    @Override
    public boolean offer(T t) {
        Objects.requireNonNull(t);

        if (contains(t)) {
            return false;
        }

        int i = size();
        heap.add(t);

        up(i, t);

        return true;
    }

    @Nullable
    @Override
    public T poll() {
        if (isEmpty()) {
            return null;
        }

        T min = heap.getFirst();
        index.removeInt(min);

        T last = heap.removeLast();

        if (!isEmpty()) {
            heap.set(0, last);
            index.put(last, 0);

            down(0, last);
        }

        return min;
    }

    @Nullable
    @Override
    public T peek() {
        if (isEmpty()) {
            return null;
        }

        return heap.getFirst();
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    @Override
    public boolean contains(Object o) {
        return index.containsKey(o);
    }

    public void update(T node) {
        int i = index.getInt(node);

        if (i == -1) return;

        int j = up(i, node);

        if (i != j) return;

        down(i, node);
    }

    private int up(int i, T t) {
        if (comparator != null) {
            return upComparator(i, t, comparator);
        }

        return upComparable(i, t);
    }

    private int upComparator(int i, T t, @NotNull Comparator<T> comparator) {
        while (i > 0) {
            int j = (i - 1) >>> 1;
            T parent = heap.get(j);

            // position found
            if (comparator.compare(t, parent) >= 0) break;

            heap.set(i, parent);
            index.put(parent, i);
            i = j;
        }

        heap.set(i, t);
        index.put(t, i);

        return i;
    }

    @SuppressWarnings("unchecked")
    private int upComparable(int i, T t) {
        var key = (Comparable<? super T>) t;

        while (i > 0) {
            int j = (i - 1) >>> 1;
            T parent = heap.get(j);

            // position found
            if (key.compareTo(parent) >= 0) break;

            heap.set(i, parent);
            index.put(parent, i);
            i = j;
        }

        heap.set(i, t);
        index.put(t, i);

        return i;
    }

    private int down(int i, T t) {
        if (comparator != null) {
            return downComparator(i, t, comparator);
        }

        return downComparable(i, t);
    }

    private int downComparator(int i, T t, @NotNull Comparator<T> comparator) {
        final int size = size();

        while (i < size) {
            int lc = (i << 1) + 1;
            int rc = lc + 1;

            int least = i;

            if (lc < size && comparator.compare(t, heap.get(lc)) > 0) {
                least = lc;
            }

            if (rc < size && comparator.compare(t, heap.get(rc)) > 0) {
                least = rc;
            }

            if (least == i) break;

            T child = heap.get(least);
            heap.set(i, child);
            index.put(child, i);

            i = least;
        }

        heap.set(i, t);
        index.put(t, i);

        return i;
    }

    @SuppressWarnings("unchecked")
    private int downComparable(int i, T t) {
        final int size = size();

        while (i < size) {
            int lc = (i << 1) + 1;
            int rc = lc + 1;

            int least = i;

            if (lc < size && ((Comparable<? super T>) heap.get(lc)).compareTo(heap.get(least)) < 0) {
                least = lc;
            }

            if (rc < size && ((Comparable<? super T>) heap.get(rc)).compareTo(heap.get(least)) < 0) {
                least = rc;
            }

            if (least == i) break;

            T child = heap.get(least);
            heap.set(i, child);
            index.put(child, i);

            i = least;
        }

        heap.set(i, t);
        index.put(t, i);

        return i;
    }
}
