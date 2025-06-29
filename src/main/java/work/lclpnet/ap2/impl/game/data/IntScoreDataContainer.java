package work.lclpnet.ap2.impl.game.data;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.event.IntScoreEvent;
import work.lclpnet.ap2.api.game.data.*;
import work.lclpnet.ap2.impl.game.data.entry.IntScoreDataEntry;
import work.lclpnet.ap2.impl.game.data.entry.ScoreView;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A data container that orders the subjects by their integer score.
 * The scores can still be updated after a subject was added.
 * The subject with the highest score is the winner.
 */
public class IntScoreDataContainer<T, Ref extends SubjectRef> extends BaseDataContainer<T, Ref> implements IntDataContainer<T, Ref>  {

    private final Object2IntMap<Ref> scoreMap = new Object2IntOpenHashMap<>();
    private final List<IntScoreEvent<T>> listeners = new ArrayList<>();
    private final Ordering ordering;
    private final @Nullable String detailKey;

    public IntScoreDataContainer(SubjectRefFactory<T, Ref> refs) {
        this(refs, Ordering.DESCENDING);
    }

    public IntScoreDataContainer(SubjectRefFactory<T, Ref> refs, Ordering ordering) {
        this(refs, ordering, null);
    }

    public IntScoreDataContainer(SubjectRefFactory<T, Ref> refs, Ordering ordering, @Nullable String detailKey) {
        super(refs);
        this.ordering = Objects.requireNonNull(ordering);
        this.detailKey = detailKey;
    }

    public void setScore(T subject, int score) {
        Ref ref = refs.create(subject);

        setScore(ref, score);

        listeners.forEach(listener -> listener.accept(subject, score));
    }

    public synchronized void setScore(Ref ref, int score) {
        scoreMap.put(ref, score);
    }

    @Override
    public void addScore(T subject, int add) {
        Ref key = refs.create(subject);

        int score = addScore(key, add);

        listeners.forEach(listener -> listener.accept(subject, score));
    }

    public synchronized int addScore(Ref ref, int add) {
        return scoreMap.compute(ref, (r, score) -> (score != null ? score : 0) + add);
    }

    @Override
    public int getScore(T subject) {
        return getScore(refs.create(subject));
    }

    public synchronized @NotNull Integer getScore(Ref ref) {
        return scoreMap.computeIfAbsent(ref, r -> 0);
    }

    @Override
    public synchronized Optional<DataEntry<Ref>> getEntry(Ref ref) {
        if (!scoreMap.containsKey(ref)) {
            return Optional.empty();
        }

        int score = scoreMap.getInt(ref);

        return Optional.of(new IntScoreDataEntry<>(ref, score, detailKey));
    }

    @Override
    public synchronized Stream<IntScoreDataEntry<Ref>> streamOrderedEntries() {
        return scoreMap.object2IntEntrySet().stream()
                .map(e -> new IntScoreDataEntry<>(e.getKey(), e.getIntValue(), detailKey))
                .sorted(ordering.orderInt(ScoreView::score));
    }

    @Override
    public void add(T subject) {
        identityIfAbsent(subject);
    }

    @Override
    public void identityIfAbsent(T subject) {
        addScore(subject, 0);
    }

    @Override
    public synchronized void clear() {
        scoreMap.clear();
    }

    public synchronized Optional<Integer> getBestScore() {
        return ordering.best(scores());
    }

    public synchronized Optional<Integer> getWorstScore() {
        return ordering.opposite().best(scores());
    }

    private synchronized IntStream scores() {
        return scoreMap.values().intStream();
    }

    public synchronized Set<T> getBestSubjects(SubjectRefResolver<T, Ref> resolver) {
        Optional<Integer> best = getBestScore();

        if (best.isEmpty()) return Set.of();

        final int bestScore = best.get();

        return scoreMap.keySet().stream()
                .filter(ref -> scoreMap.getInt(ref) == bestScore)
                .map(resolver::resolve)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void register(IntScoreEvent<T> listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    @Override
    public synchronized DataContainer<T, Ref> copy() {
        var copy = new IntScoreDataContainer<>(refs, ordering, detailKey);
        copy.scoreMap.putAll(this.scoreMap);

        return copy;
    }
}
