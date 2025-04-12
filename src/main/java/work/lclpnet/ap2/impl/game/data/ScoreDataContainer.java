package work.lclpnet.ap2.impl.game.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.event.IntScoreEvent;
import work.lclpnet.ap2.api.event.IntScoreEventSource;
import work.lclpnet.ap2.api.game.data.*;
import work.lclpnet.ap2.api.game.sink.IntDataSink;
import work.lclpnet.ap2.impl.game.data.entry.ScoreDataEntry;
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
public class ScoreDataContainer<T, Ref extends SubjectRef> extends BaseDataContainer<T, Ref> implements IntScoreEventSource<T>, IntDataSink<T> {

    private final Map<Ref, Integer> scoreMap = new HashMap<>();
    private final List<IntScoreEvent<T>> listeners = new ArrayList<>();
    private final Ordering ordering;
    private final @Nullable String detailKey;

    public ScoreDataContainer(SubjectRefFactory<T, Ref> refs) {
        this(refs, Ordering.DESCENDING);
    }

    public ScoreDataContainer(SubjectRefFactory<T, Ref> refs, Ordering ordering) {
        this(refs, ordering, null);
    }

    public ScoreDataContainer(SubjectRefFactory<T, Ref> refs, Ordering ordering, @Nullable String detailKey) {
        super(refs);
        this.ordering = Objects.requireNonNull(ordering);
        this.detailKey = detailKey;
    }

    public void setScore(T subject, int score) {
        synchronized (this) {
            scoreMap.put(refs.create(subject), score);
        }

        listeners.forEach(listener -> listener.accept(subject, score));
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
        Integer score = scoreMap.get(ref);

        if (score == null) {
            return Optional.empty();
        }

        return Optional.of(new ScoreDataEntry<>(ref, score, detailKey));
    }

    @Override
    public synchronized Stream<? extends DataEntry<Ref>> streamOrderedEntries() {
        return scoreMap.entrySet().stream()
                .map(e -> new ScoreDataEntry<>(e.getKey(), e.getValue(), detailKey))
                .sorted(ordering.order(ScoreView::score));
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

    public synchronized OptionalInt getBestScore() {
        return ordering.best(scores());
    }

    public synchronized OptionalInt getWorstScore() {
        return ordering.opposite().best(scores());
    }

    private synchronized IntStream scores() {
        return scoreMap.values().stream().mapToInt(i -> i);
    }

    public synchronized Set<T> getBestSubjects(SubjectRefResolver<T, Ref> resolver) {
        OptionalInt best = getBestScore();

        if (best.isEmpty()) return Set.of();

        final int bestScore = best.getAsInt();

        return scoreMap.keySet().stream()
                .filter(ref -> scoreMap.get(ref) == bestScore)
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
        var copy = new ScoreDataContainer<>(refs, ordering, detailKey);
        copy.scoreMap.putAll(this.scoreMap);

        return copy;
    }
}
