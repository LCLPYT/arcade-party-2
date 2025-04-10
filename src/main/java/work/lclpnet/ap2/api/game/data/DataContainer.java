package work.lclpnet.ap2.api.game.data;

import com.google.common.collect.AbstractIterator;
import it.unimi.dsi.fastutil.objects.ObjectIntPair;
import work.lclpnet.ap2.impl.util.RankUtil;

import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public interface DataContainer<T, Ref extends SubjectRef> {

    void delete(T subject);

    Optional<DataEntry<Ref>> getEntry(T subject);

    Stream<? extends DataEntry<Ref>> streamOrderedEntries();

    void freeze();

    void ensureTracked(T subject);

    void clear();

    default Optional<T> getBestSubject(SubjectRefResolver<T, Ref> resolver) {
        return streamOrderedEntries().findFirst()
                .map(DataEntry::subject)
                .map(resolver::resolve);
    }

    default Stream<T> getEqualScoreSubjects(T player, SubjectRefResolver<T, Ref> resolver) {
        return getEntry(player).map(entry -> streamOrderedEntries()
                .filter(entry::scoreEquals)
                .map(DataEntry::subject)
                .map(resolver::resolve)
                .filter(Objects::nonNull)).orElseGet(Stream::empty);
    }

    default Stream<Set<ObjectIntPair<Ref>>> streamEntriesRanked() {
        return RankUtil.rank(this::streamRankedEntries, ObjectIntPair::rightInt);
    }

    default Stream<ObjectIntPair<Ref>> streamRankedEntries() {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(getRankedEntries(), 0), false);
    }

    default Iterator<ObjectIntPair<Ref>> getRankedEntries() {
        var parent = streamOrderedEntries().iterator();

        return new AbstractIterator<>() {
            int rank = 1;
            int skippedRanks = 0;
            DataEntry<Ref> prevEntry = null;

            @Override
            protected ObjectIntPair<Ref> computeNext() {
                if (!parent.hasNext()) {
                    endOfData();
                    return null;
                }

                DataEntry<Ref> dataEntry = parent.next();

                if (prevEntry != null) {
                    if (prevEntry.scoreEquals(dataEntry)) {
                        skippedRanks++;
                    } else {
                        rank = rank + skippedRanks + 1;
                        skippedRanks = 0;
                    }
                }

                prevEntry = dataEntry;

                return ObjectIntPair.of(dataEntry.subject(), rank);
            }
        };
    }
}
