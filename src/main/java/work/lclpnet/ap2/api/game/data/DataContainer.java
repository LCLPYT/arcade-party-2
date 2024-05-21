package work.lclpnet.ap2.api.game.data;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public interface DataContainer<T, Ref extends SubjectRef> {

    void delete(T subject);

    Optional<DataEntry<Ref>> getEntry(T subject);

    Stream<? extends DataEntry<Ref>> orderedEntries();

    void freeze();

    void ensureTracked(T subject);

    void clear();

    default Optional<T> getBestSubject(SubjectRefResolver<T, Ref> resolver) {
        return orderedEntries().findFirst()
                .map(DataEntry::subject)
                .map(resolver::resolve);
    }

    default Stream<T> getEqualScoreSubjects(T player, SubjectRefResolver<T, Ref> resolver) {
        return getEntry(player).map(entry -> orderedEntries()
                .filter(entry::scoreEquals)
                .map(DataEntry::subject)
                .map(resolver::resolve)
                .filter(Objects::nonNull)).orElseGet(Stream::empty);
    }
}
