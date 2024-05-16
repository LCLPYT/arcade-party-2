package work.lclpnet.ap2.impl.game.data;

import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.game.data.DataEntry;
import work.lclpnet.ap2.api.game.data.SubjectRef;
import work.lclpnet.ap2.api.game.data.SubjectRefFactory;
import work.lclpnet.ap2.impl.game.data.entry.SimpleOrderDataEntry;
import work.lclpnet.kibu.translate.text.TranslatedText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A data container for last one standing game modes.
 * The last added subject is the winner.
 */
public class EliminationDataContainer<T, Ref extends SubjectRef> implements DataContainer<T, Ref> {

    private final SubjectRefFactory<T, Ref> refs;
    private final Object2IntMap<Ref> index = new Object2IntArrayMap<>();
    private final List<Map<Ref, SimpleOrderDataEntry<Ref>>> order = new ArrayList<>();
    private boolean frozen = false;

    public EliminationDataContainer(SubjectRefFactory<T, Ref> refs) {
        this.refs = refs;
    }

    public void eliminated(T subject) {
        eliminated(subject, null);
    }

    public void eliminated(T subject, TranslatedText data) {
        allEliminated(List.of(subject), data);
    }

    public void allEliminated(Iterable<? extends T> subjects) {
        allEliminated(subjects, null);
    }

    public void allEliminated(Iterable<? extends T> subjects, TranslatedText data) {
        synchronized (this) {
            if (frozen) return;

            int rank = order.size();
            Map<Ref, SimpleOrderDataEntry<Ref>> mapping = new HashMap<>();

            for (T subject : subjects) {
                Ref ref = refs.create(subject);

                if (index.containsKey(ref)) continue;

                index.put(ref, rank);
                mapping.put(ref, new SimpleOrderDataEntry<>(ref, rank, data));
            }

            if (mapping.isEmpty()) return;

            order.add(mapping);
        }
    }

    @Override
    public void delete(T subject) {
        synchronized (this) {
            if (frozen) return;

            Ref ref = refs.create(subject);
            int rank = getRank(ref);

            if (rank < 0 || rank >= order.size()) return;

            index.removeInt(ref);

            var entries = order.get(rank);

            if (entries == null) return;

            entries.remove(ref);
        }
    }

    @Override
    public DataEntry<Ref> getEntry(T subject) {
        Ref ref = refs.create(subject);

        synchronized (this) {
            int rank = getRank(ref);

            if (rank < 0 || rank >= order.size()) {
                return new SimpleOrderDataEntry<>(ref, -1);
            }

            var mapping = order.get(rank);

            if (mapping == null) {
                return new SimpleOrderDataEntry<>(ref, rank);
            }

            var dataEntry = mapping.get(ref);

            if (dataEntry == null) {
                return new SimpleOrderDataEntry<>(ref, rank);
            }

            return dataEntry;
        }
    }

    @Override
    public Stream<DataEntry<Ref>> orderedEntries() {
        synchronized (this) {
            return this.order.stream().flatMap(mapping -> mapping.values().stream());
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
        eliminated(subject);
    }

    private int getRank(Ref ref) {
        return index.getOrDefault(ref, -1);
    }
}
