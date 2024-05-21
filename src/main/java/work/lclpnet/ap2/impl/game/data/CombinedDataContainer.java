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
    private boolean frozen = false;

    public CombinedDataContainer(List<DataContainer<T, Ref>> children) {
        if (children.isEmpty()) {
            throw new IllegalArgumentException("There needs to be at least on child data container");
        }

        this.children = children;
    }

    @Override
    public void delete(T subject) {
        synchronized (this) {
            if (frozen) return;

            for (var child : children) {
                child.delete(subject);
            }
        }
    }

    @Override
    public Optional<DataEntry<Ref>> getEntry(T subject) {
        synchronized (this) {
            for (var child : children) {
                var entry = child.getEntry(subject);

                if (entry.isPresent()) {
                    return entry;
                }
            }

            return Optional.empty();
        }
    }

    @Override
    public Stream<? extends DataEntry<Ref>> orderedEntries() {
        Set<Ref> seen = new HashSet<>();

        synchronized (this) {
            return children.stream()
                    .flatMap(DataContainer::orderedEntries)
                    .filter(entry -> seen.add(entry.subject()));  // each entry only once. this is stateful, only sequential streams will work
        }
    }

    @Override
    public void freeze() {
        synchronized (this) {
            frozen = true;

            for (var child : children) {
                child.freeze();
            }
        }
    }

    @Override
    public void ensureTracked(T subject) {
        synchronized (this) {
            // make sure the subject is tracked by the last child container
            var lastChild = children.getLast();

            lastChild.ensureTracked(subject);
        }
    }

    @Override
    public void clear() {
        synchronized (this) {
            if (frozen) return;

            for (var child : children) {
                child.clear();
            }
        }
    }
}
