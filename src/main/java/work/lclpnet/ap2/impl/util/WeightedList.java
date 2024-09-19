package work.lclpnet.ap2.impl.util;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatList;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class WeightedList<E> extends AbstractList<E> {

    private final List<E> elements;
    private final FloatList cumulativeWeights;  // is always ordered ascending
    private float totalWeight;

    public WeightedList() {
        this(10);
    }

    public WeightedList(int initialCapacity) {
        this(new ArrayList<>(initialCapacity), new FloatArrayList(initialCapacity), 0);
    }

    private WeightedList(List<E> elements, FloatList cumulativeWeights, float totalWeight) {
        this.elements = elements;
        this.cumulativeWeights = cumulativeWeights;
        this.totalWeight = totalWeight;
    }

    @Override
    public synchronized int size() {
        return elements.size();
    }

    @Override
    public synchronized E get(int index) {
        return elements.get(index);
    }

    public void add(E item, float weight) {
        if (weight < 0f) throw new IllegalArgumentException("Weight must be positive");
        else if (!Float.isFinite(weight)) throw new IllegalArgumentException("Weight must be finite");

        synchronized (this) {
            elements.add(item);
            totalWeight += weight;
            cumulativeWeights.add(totalWeight);
        }
    }

    @Override
    public synchronized E remove(int index) {
        Objects.checkIndex(index, elements.size());

        E element = elements.remove(index);
        float offset = cumulativeWeights.removeFloat(index);

        float cumulativeWeight = index > 0 ? cumulativeWeights.getFloat(index - 1) : 0f;

        // update subsequent cumulative weights
        for (int i = index, size = cumulativeWeights.size(); i < size; i++) {
            float oldCumulativeWeight = cumulativeWeights.getFloat(i);
            float weight = oldCumulativeWeight - offset;
            offset = oldCumulativeWeight;

            cumulativeWeight += weight;

            cumulativeWeights.set(i, cumulativeWeight);
        }

        return element;
    }

    @Override
    public synchronized void clear() {
        elements.clear();
        cumulativeWeights.clear();
        totalWeight = 0;
    }

    @Nullable
    public E getRandomElement(Random random) {
        int index = getRandomIndex(random);

        if (index == -1) return null;

        return get(index);
    }

    public synchronized int getRandomIndex(Random random) {
        final float target = random.nextFloat() * totalWeight;

        int index = binarySearch(cumulativeWeights, target);

        if (index < 0) {
            index = -index - 1;
        }

        if (index >= elements.size()) {
            return -1;
        }

        return index;
    }

    @VisibleForTesting
    static int binarySearch(FloatList list, float target) {
        int lo = 0;
        int hi = list.size() - 1;

        while (lo <= hi) {
            int mid = (hi + lo) >>> 1;
            float val = list.getFloat(mid);
            int cmp = Float.compare(val, target);

            if (cmp < 0) {
                if (mid < hi && target <= list.getFloat(mid + 1)) {
                    return mid + 1;
                }

                lo = mid + 1;
            } else if (cmp > 0) {
                float start = mid > 0 ? list.getFloat(mid - 1) : 0.0f;

                if (target > start) {
                    return mid;
                }

                hi = mid - 1;
            } else {
                return mid;
            }
        }

        return -1;
    }

    public synchronized <U> WeightedList<U> map(Function<E, U> mapper) {
        List<U> mappedElements = this.elements.stream()
                .map(mapper)
                .collect(Collectors.toCollection(ArrayList::new));

        return new WeightedList<>(mappedElements, cumulativeWeights, totalWeight);
    }

    public synchronized WeightedList<E> filter(Predicate<E> predicate) {
        int[] indices = IntStream.range(0, this.elements.size())
                .filter(i -> predicate.test(get(i)))
                .toArray();

        List<E> filtered = new ArrayList<>(indices.length);
        FloatList filteredCumulativeWeights = new FloatArrayList(indices.length);

        float totalWeight = 0;

        for (int i : indices) {
            filtered.add(elements.get(i));
            float offset = i > 0 ? cumulativeWeights.getFloat(i - 1) : 0f;
            float weight = cumulativeWeights.getFloat(i) - offset;
            totalWeight += weight;
            filteredCumulativeWeights.add(totalWeight);
        }

        return new WeightedList<>(filtered, filteredCumulativeWeights, totalWeight);
    }
}
