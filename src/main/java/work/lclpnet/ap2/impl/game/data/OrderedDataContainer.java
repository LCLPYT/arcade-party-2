package work.lclpnet.ap2.impl.game.data;

import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.game.data.DataEntry;
import work.lclpnet.ap2.api.game.data.SubjectRef;
import work.lclpnet.ap2.api.game.data.SubjectRefFactory;
import work.lclpnet.ap2.impl.game.data.entry.SimpleDataEntry;
import work.lclpnet.kibu.translate.text.TranslatedText;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A data container that keeps the order the subjects were added.
 * The first added subject is the winner.
 */
public class OrderedDataContainer<T, Ref extends SubjectRef> implements DataContainer<T, Ref> {

    private final SubjectRefFactory<T, Ref> refs;
    private final Map<Ref, Entry<Ref>> order = new HashMap<>();
    private boolean frozen = false;

    public OrderedDataContainer(SubjectRefFactory<T, Ref> refs) {
        this.refs = refs;
    }

    public void add(T subject) {
        add(subject, SimpleDataEntry::new);
    }

    public void add(T subject, TranslatedText data) {
        add(subject, ref -> new SimpleDataEntry<>(ref, data));
    }

    private void add(T subject, Function<Ref, SimpleDataEntry<Ref>> entryFactory) {
        synchronized (this) {
            if (frozen) return;

            Ref ref = refs.create(subject);

            if (order.containsKey(ref)) return;

            var dataEntry = entryFactory.apply(ref);

            order.put(ref, new Entry<>(order.size(), dataEntry));
        }
    }

    @Override
    public void delete(T subject) {
        synchronized (this) {
            if (frozen) return;

            order.remove(refs.create(subject));
        }
    }

    @Override
    public DataEntry<Ref> getEntry(T subject) {
        synchronized (this) {
            Ref ref = refs.create(subject);
            var entry = order.get(ref);

            if (entry == null) {
                return new SimpleDataEntry<>(ref);
            }

            return entry.dataEntry();
        }
    }

    @Override
    public Stream<? extends DataEntry<Ref>> orderedEntries() {
        synchronized (this) {
            return order.values().stream()
                    .sorted(Comparator.comparingInt(Entry::order))
                    .map(Entry::dataEntry);
        }
    }

    @Override
    public void freeze() {
        synchronized (this) {
            frozen = true;
        }
    }

    @Override
    public void ensureTracked(T subject) {
        add(subject);
    }

    @Override
    public void clear() {
        synchronized (this) {
            if (frozen) return;

            order.clear();
        }
    }

    private record Entry<Ref extends SubjectRef>(int order, SimpleDataEntry<Ref> dataEntry) {}
}
