package work.lclpnet.ap2.impl.game.data;

import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.game.data.DataEntry;
import work.lclpnet.ap2.api.game.data.SubjectRef;
import work.lclpnet.ap2.api.game.data.SubjectRefFactory;
import work.lclpnet.ap2.impl.game.data.entry.SupremeDataEntry;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class SupremeDataContainer<T, Ref extends SubjectRef> extends BaseDataContainer<T, Ref> {

    private final Set<Ref> entries = new HashSet<>();

    public SupremeDataContainer(SubjectRefFactory<T, Ref> refs) {
        super(refs);
    }

    @Override
    public synchronized void add(T subject) {
        entries.add(refs.create(subject));
    }

    @Override
    public synchronized Optional<DataEntry<Ref>> getEntry(Ref ref) {
        if (entries.contains(ref)) {
            return Optional.of(new SupremeDataEntry<>(ref));
        }

        return Optional.empty();
    }

    @Override
    public synchronized Stream<? extends DataEntry<Ref>> streamOrderedEntries() {
        return entries.stream().map(SupremeDataEntry::new);
    }

    @Override
    public void identityIfAbsent(T subject) {
        // NOOP
    }

    @Override
    public synchronized void clear() {
        entries.clear();
    }

    @Override
    public synchronized DataContainer<T, Ref> copy() {
        var copy = new SupremeDataContainer<>(refs);
        copy.entries.addAll(this.entries);

        return copy;
    }
}
