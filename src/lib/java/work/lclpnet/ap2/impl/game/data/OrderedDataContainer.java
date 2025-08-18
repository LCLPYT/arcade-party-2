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
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A data container that keeps the order the subjects were added.
 * The first added subject is the winner.
 */
public class OrderedDataContainer<T, Ref extends SubjectRef> extends BaseDataContainer<T, Ref> {

    private final Map<Ref, Entry<Ref>> order = new HashMap<>();

    public OrderedDataContainer(SubjectRefFactory<T, Ref> refs) {
        super(refs);
    }

    @Override
    public void add(T subject) {
        add(subject, SimpleDataEntry::new);
    }

    public void add(T subject, TranslatedText data) {
        add(subject, ref -> new SimpleDataEntry<>(ref, data));
    }

    private synchronized void add(T subject, Function<Ref, SimpleDataEntry<Ref>> entryFactory) {
        Ref ref = refs.create(subject);

        if (order.containsKey(ref)) return;

        var dataEntry = entryFactory.apply(ref);

        order.put(ref, new Entry<>(order.size(), dataEntry));
    }

    @Override
    public synchronized Optional<DataEntry<Ref>> getEntry(Ref ref) {
        var entry = order.get(ref);

        if (entry == null) {
            return Optional.empty();
        }

        return Optional.of(entry.dataEntry());
    }

    @Override
    public synchronized Stream<? extends DataEntry<Ref>> streamOrderedEntries() {
        return order.values().stream()
                .sorted(Comparator.comparingInt(Entry::order))
                .map(Entry::dataEntry);
    }

    @Override
    public void identityIfAbsent(T subject) {
        // NOOP
    }

    @Override
    public synchronized void clear() {
        order.clear();
    }

    @Override
    public synchronized DataContainer<T, Ref> copy() {
        var copy = new OrderedDataContainer<>(refs);
        copy.order.putAll(this.order);

        return copy;
    }

    private record Entry<Ref extends SubjectRef>(int order, SimpleDataEntry<Ref> dataEntry) {}
}
