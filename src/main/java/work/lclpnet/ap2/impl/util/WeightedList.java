package work.lclpnet.ap2.impl.util;

import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class WeightedList<E> extends AbstractList<E> {

    private final List<Entry<E>> entries;
    private float totalWeight;

    public WeightedList() {
        this(new ArrayList<>(), 0);
    }

    public WeightedList(int initialCapacity) {
        this(new ArrayList<>(initialCapacity), 0);
    }

    private WeightedList(List<Entry<E>> entries, float totalWeight) {
        this.entries = entries;
        this.totalWeight = totalWeight;
    }

    @Override
    public int size() {
        synchronized (this) {
            return entries.size();
        }
    }

    @Override
    public E get(int index) {
        synchronized (this) {
            return entries.get(index).item;
        }
    }

    public void add(E item, float weight) {
        if (weight < 0f) throw new IllegalArgumentException("Weight must be positive");

        synchronized (this) {
            entries.add(new Entry<>(item, weight));
            totalWeight += weight;
        }
    }

    @Override
    public E remove(int index) {
        synchronized (this) {
            Objects.checkIndex(index, entries.size());

            Entry<E> entry = entries.remove(index);

            totalWeight -= entry.weight;

            return entry.item;
        }
    }

    @Nullable
    public E getRandomElement(Random random) {
        int index = getRandomIndex(random);

        if (index == -1) return null;

        return get(index);
    }

    public int getRandomIndex(Random random) {
        synchronized (this) {
            final float target = random.nextFloat() * totalWeight;
            float offset = 0f;

            for (int i = 0, size = entries.size(); i < size; i++) {
                var entry = entries.get(i);

                if (target <= offset + entry.weight) {
                    return i;
                }

                offset += entry.weight;
            }

            return -1;
        }
    }

    public <U> WeightedList<U> map(Function<E, U> mapper) {
        synchronized (this) {
            List<Entry<U>> mappedEntries = this.entries.stream()
                    .map(entry -> new Entry<>(mapper.apply(entry.item), entry.weight))
                    .collect(Collectors.toCollection(ArrayList::new));

            return new WeightedList<>(mappedEntries, totalWeight);
        }
    }

    public WeightedList<E> filter(Predicate<E> predicate) {
        synchronized (this) {
            List<Entry<E>> filtered = this.entries.stream()
                    .filter(entry -> predicate.test(entry.item))
                    .collect(Collectors.toCollection(ArrayList::new));

            float filteredWeight = filtered.stream()
                    .reduce(0f, (accumulated, entry) -> accumulated + entry.weight, Float::sum);

            return new WeightedList<>(filtered, filteredWeight);
        }
    }

    private record Entry<T>(T item, float weight) {}
}
