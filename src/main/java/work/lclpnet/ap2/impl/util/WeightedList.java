package work.lclpnet.ap2.impl.util;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WeightedList<T> {

    private final List<Entry<T>> entries = new ArrayList<>();
    private float totalWeight = 0f;

    public void add(T item, float weight) {
        if (weight < 0f) throw new IllegalArgumentException("Weight must be positive");

        entries.add(new Entry<>(item, weight));
        totalWeight += weight;
    }

    @Nullable
    public T getRandomElement(Random random) {
        final float target = random.nextFloat() * totalWeight;
        float offset = 0f;

        for (var entry : entries) {
            if (target <= offset + entry.weight) {
                return entry.item;
            }

            offset += entry.weight;
        }

        return null;
    }

    private record Entry<T>(T item, float weight) {}
}
