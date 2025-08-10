package work.lclpnet.ap2.impl.game.data;

import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.game.data.DataEntry;
import work.lclpnet.ap2.api.game.data.SubjectRef;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class CombinedDataContainer<T, Ref extends SubjectRef> implements DataContainer<T, Ref> {

    private final List<DataContainer<T, Ref>> children;

    public CombinedDataContainer(List<DataContainer<T, Ref>> children) {
        if (children.isEmpty()) {
            throw new IllegalArgumentException("There needs to be at least on child data container");
        }

        this.children = List.copyOf(children);
    }

    @Override
    public synchronized Optional<DataEntry<Ref>> getEntry(T subject) {
        for (var child : children) {
            var entry = child.getEntry(subject);

            if (entry.isPresent()) {
                return entry;
            }
        }

        return Optional.empty();
    }

    @Override
    public synchronized Optional<DataEntry<Ref>> getEntry(Ref ref) {
        for (var child : children) {
            var entry = child.getEntry(ref);

            if (entry.isPresent()) {
                return entry;
            }
        }

        return Optional.empty();
    }

    @Override
    public synchronized Stream<? extends DataEntry<Ref>> streamOrderedEntries() {
        Set<Ref> seen = new HashSet<>();

        return children.stream()
                .flatMap(DataContainer::streamOrderedEntries)
                .filter(entry -> seen.add(entry.subject()));  // each entry only once. this is stateful, only sequential streams will work
    }

    @Override
    public void add(T subject) {
        children.getFirst().add(subject);
    }

    @Override
    public void identityIfAbsent(T subject) {
        children.getLast().identityIfAbsent(subject);
    }

    @Override
    public synchronized void clear() {
        for (var child : children) {
            child.clear();
        }
    }

    @Override
    public synchronized DataContainer<T, Ref> copy() {
        return new CombinedDataContainer<>(children.stream().map(DataContainer::copy).toList());
    }
}
