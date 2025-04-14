package work.lclpnet.ap2.impl.game.data;

import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.game.data.DataEntry;
import work.lclpnet.ap2.api.game.data.SubjectRef;
import work.lclpnet.ap2.api.game.data.SubjectRefFactory;
import work.lclpnet.ap2.impl.game.data.entry.SimpleOrderDataEntry;
import work.lclpnet.kibu.translate.text.TranslatedText;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A data container for last one standing game modes.
 * The last added subject is the winner.
 */
public class EliminationDataContainer<T, Ref extends SubjectRef> extends BaseDataContainer<T, Ref> {

    private final Object2IntMap<Ref> index = new Object2IntArrayMap<>();
    private final List<Map<Ref, SimpleOrderDataEntry<Ref>>> order = new ArrayList<>();

    public EliminationDataContainer(SubjectRefFactory<T, Ref> refs) {
        super(refs);
    }

    @Override
    public void add(T subject) {
        add(subject, null);
    }

    public void add(T subject, TranslatedText data) {
        addAll(List.of(subject), data);
    }

    public void addAll(Iterable<? extends T> subjects) {
        addAll(subjects, null);
    }

    public synchronized void addAll(Iterable<? extends T> subjects, TranslatedText data) {
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

    @Override
    public synchronized Optional<DataEntry<Ref>> getEntry(Ref ref) {
        int rank = getRank(ref);

        if (rank < 0 || rank >= order.size()) {
            return Optional.empty();
        }

        var mapping = order.get(rank);

        if (mapping == null) {
            return Optional.empty();
        }

        var dataEntry = mapping.get(ref);

        return Optional.ofNullable(dataEntry);
    }

    @Override
    public synchronized Stream<DataEntry<Ref>> streamOrderedEntries() {
        // order needs to be reversed
        return IntStream.range(0, order.size())
                .mapToObj(i -> order.get(order.size() - i - 1))
                .flatMap(mapping -> mapping.values().stream());
    }

    @Override
    public void identityIfAbsent(T subject) {
        // NOOP
    }

    @Override
    public synchronized void clear() {
        index.clear();
        order.clear();
    }

    private int getRank(Ref ref) {
        return index.getOrDefault(ref, -1);
    }

    @Override
    public synchronized DataContainer<T, Ref> copy() {
        var copy = new EliminationDataContainer<>(refs);
        copy.index.putAll(this.index);
        copy.order.addAll(this.order);

        return copy;
    }
}
