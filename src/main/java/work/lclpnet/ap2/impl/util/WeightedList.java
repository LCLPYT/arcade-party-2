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
    public synchronized int size() {
        return entries.size();
    }

    @Override
    public synchronized E get(int index) {
        return entries.get(index).item;
    }

    public void add(E item, float weight) {
        if (weight < 0f) throw new IllegalArgumentException("Weight must be positive");

        synchronized (this) {
            entries.add(new Entry<>(item, weight));
            totalWeight += weight;
        }
    }

    @Override
    public synchronized E remove(int index) {
        Objects.checkIndex(index, entries.size());

        Entry<E> entry = entries.remove(index);

        totalWeight -= entry.weight;

        return entry.item;
    }

    @Override
    public synchronized void clear() {
        entries.clear();
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

    public synchronized <U> WeightedList<U> map(Function<E, U> mapper) {
        List<Entry<U>> mappedEntries = this.entries.stream()
                .map(entry -> new Entry<>(mapper.apply(entry.item), entry.weight))
                .collect(Collectors.toCollection(ArrayList::new));

        return new WeightedList<>(mappedEntries, totalWeight);
    }

    public synchronized WeightedList<E> filter(Predicate<E> predicate) {
        List<Entry<E>> filtered = this.entries.stream()
                .filter(entry -> predicate.test(entry.item))
                .collect(Collectors.toCollection(ArrayList::new));

        float filteredWeight = filtered.stream()
                .reduce(0f, (accumulated, entry) -> accumulated + entry.weight, Float::sum);

        return new WeightedList<>(filtered, filteredWeight);
    }

    private record Entry<T>(T item, float weight) {}
}
