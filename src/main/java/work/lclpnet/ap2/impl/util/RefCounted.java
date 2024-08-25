package work.lclpnet.ap2.impl.util;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A reference counter that is backed by a {@link Map}.
 * This class provides two methods:
 * <ul>
 *     <li>{@link #reference(Object, Function)}</li> increases the reference count by one,
 *     computing a mapping if there was no reference to the key yet.
 *     <li>{@link #dereference(Object)}</li> decreases the reference count by one,
 *     removing the mapping if there are no references remaining to the key.
 * </ul>
 *
 * @param <K> The key type.
 * @param <V> The value type.
 * @implNote This implementation is synchronized by default. The underlying map doesn't have to be synchronized.
 * @apiNote The underlying map must not be modified externally.
 */
public class RefCounted<K, V> {

    private final Map<K, V> map;
    @VisibleForTesting final Object2IntMap<K> refCount = new Object2IntOpenHashMap<>();

    public RefCounted(Supplier<Map<K, V>> mapFactory) {
        this.map = mapFactory.get();
    }

    public synchronized V reference(K key, Function<K, V> valueFunction) {
        V value = map.computeIfAbsent(key, valueFunction);

        refCount.compute(key, (k, count) -> count == null ? 1 : count + 1);

        return value;
    }

    public synchronized void dereference(K key) {
        Integer newCount = refCount.compute(key, (k, count) -> count == null || count <= 1 ? null : count - 1);

        if (newCount != null) return;

        map.remove(key);
    }

    public void forEach(Consumer<V> action) {
        map.values().forEach(action);
    }

    public void forEach(BiConsumer<K, V> action) {
        map.forEach(action);
    }
}
