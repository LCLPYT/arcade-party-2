package work.lclpnet.ap2.api.game.data;

import java.util.Optional;
import java.util.stream.Stream;

public interface DataContainer<T, Ref extends SubjectRef> {

    void delete(T subject);

    DataEntry<Ref> getEntry(T subject);

    Stream<? extends DataEntry<Ref>> orderedEntries();

    void freeze();

    void ensureTracked(T subject);

    default Optional<T> getBestSubject(SubjectRefResolver<T, Ref> resolver) {
        return orderedEntries().findFirst()
                .map(DataEntry::subject)
                .map(resolver::resolve);
    }
}
