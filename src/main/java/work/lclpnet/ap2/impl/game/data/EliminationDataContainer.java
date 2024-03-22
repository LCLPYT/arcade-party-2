package work.lclpnet.ap2.impl.game.data;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.game.data.DataEntry;
import work.lclpnet.ap2.api.game.data.SubjectRef;
import work.lclpnet.ap2.api.game.data.SubjectRefFactory;
import work.lclpnet.ap2.impl.game.data.entry.SimpleOrderDataEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * A data container for last one standing game modes.
 * The last added subject is the winner.
 */
public class EliminationDataContainer<T, Ref extends SubjectRef> implements DataContainer<T, Ref> {

    private final SubjectRefFactory<T, Ref> refs;
    private final Object2IntMap<Ref> values = new Object2IntArrayMap<>();
    private final Int2ObjectMap<List<Ref>> order = new Int2ObjectArrayMap<>();
    private int index = 0;
    private boolean frozen = false;

    public EliminationDataContainer(SubjectRefFactory<T, Ref> refs) {
        this.refs = refs;
    }

    public void eliminated(T subject) {
        allEliminated(List.of(subject));
    }

    public void allEliminated(Iterable<? extends T> subjects) {
        synchronized (this) {
            if (frozen) return;

            boolean changed = false;

            for (T subject : subjects) {
                Ref ref = refs.create(subject);

                if (values.containsKey(ref)) continue;

                values.put(ref, index);

                var list = order.computeIfAbsent(index, i -> new ArrayList<>());
                list.add(ref);

                changed = true;

            }

            if (changed) {
                index++;
            }
        }
    }

    @Override
    public void delete(T subject) {
        synchronized (this) {
            if (frozen) return;

            Ref ref = refs.create(subject);
            int order = getOrder(ref);

            if (order == -1) return;

            values.removeInt(ref);

            var entries = this.order.get(order);

            if (entries == null) return;

            entries.remove(ref);
        }
    }

    private int getOrder(Ref ref) {
        return values.getOrDefault(ref, -1);
    }

    @Override
    public DataEntry<Ref> getEntry(T subject) {
        Ref ref = refs.create(subject);
        int order = getOrder(ref);
        return new SimpleOrderDataEntry<>(ref, order);
    }

    @Override
    public Stream<DataEntry<Ref>> orderedEntries() {
        Comparator<Int2ObjectMap.Entry<List<Ref>>> ascending = Comparator.comparingInt(Int2ObjectMap.Entry::getIntKey);

        return this.order.int2ObjectEntrySet().stream()
                .sorted(ascending.reversed())
                .flatMap(orderEntry -> {
                    int order = orderEntry.getIntKey();
                    return orderEntry.getValue().stream().map(ref -> new SimpleOrderDataEntry<>(ref, order));
                });
    }

    @Override
    public void freeze() {
        synchronized (this) {
            frozen = true;
        }
    }

    @Override
    public void ensureTracked(T subject) {
        eliminated(subject);
    }
}
