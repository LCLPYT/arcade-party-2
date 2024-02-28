package work.lclpnet.ap2.impl.game.data;

import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.game.data.DataEntry;
import work.lclpnet.ap2.api.game.data.SubjectRef;
import work.lclpnet.ap2.api.game.data.SubjectRefFactory;
import work.lclpnet.ap2.impl.game.data.entry.SimpleDataEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class OrderedDataContainer<T, Ref extends SubjectRef> implements DataContainer<T, Ref> {

    private final SubjectRefFactory<T, Ref> refs;
    private final List<Ref> order = new ArrayList<>();
    private boolean frozen = false;

    public OrderedDataContainer(SubjectRefFactory<T, Ref> refs) {
        this.refs = refs;
    }

    public void add(T subject) {
        synchronized (this) {
            if (frozen) return;

            Ref ref = refs.create(subject);

            if (order.contains(ref)) return;

            order.add(ref);
        }
    }

    @Override
    public void delete(T subject) {
        synchronized (this) {
            if (frozen) return;

            order.remove(refs.create(subject));  // O(n) should not really be an issue
        }
    }

    @Override
    public DataEntry<Ref> getEntry(T subject) {
        return new SimpleDataEntry<>(refs.create(subject));
    }

    @Override
    public Stream<? extends DataEntry<Ref>> orderedEntries() {
        return order.stream().map(SimpleDataEntry::new);
    }

    @Override
    public void freeze() {
        synchronized (this) {
            frozen = true;
        }
    }

    @Override
    public void ensureTracked(T subject) {
        Ref ref = refs.create(subject);

        if (order.contains(ref)) return;

        order.add(ref);
    }
}
