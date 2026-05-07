package work.lclpnet.ap2.impl.game.data;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.data.*;
import work.lclpnet.ap2.impl.game.data.entry.DoubleScoreDataEntry;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import static java.lang.Math.abs;

/**
 * A data container that orders the subjects by their integer score.
 * The scores can still be updated after a subject was added.
 * The subject with the highest score is the winner.
 */
public class DoubleScoreDataContainer<T, Ref extends SubjectRef> extends BaseDataContainer<T, Ref> implements DataContainer<T, Ref> {

    private final Object2DoubleMap<Ref> scoreMap = new Object2DoubleOpenHashMap<>();
    private final Ordering ordering;
    private final @Nullable String detailKey;
    private final String format;

    public DoubleScoreDataContainer(SubjectRefFactory<T, Ref> refs) {
        this(refs, Ordering.DESCENDING);
    }

    public DoubleScoreDataContainer(SubjectRefFactory<T, Ref> refs, Ordering ordering) {
        this(refs, ordering, null);
    }

    public DoubleScoreDataContainer(SubjectRefFactory<T, Ref> refs, Ordering ordering, @Nullable String detailKey) {
        this(refs, ordering, detailKey, "%.2f");
    }

    public DoubleScoreDataContainer(SubjectRefFactory<T, Ref> refs, Ordering ordering, @Nullable String detailKey, String format) {
        super(refs);
        this.ordering = Objects.requireNonNull(ordering);
        this.detailKey = detailKey;
        this.format = format;
    }

    public void setScore(T subject, double score) {
        Ref ref = refs.create(subject);

        setScore(ref, score);
    }

    public synchronized void setScore(Ref ref, double score) {
        scoreMap.put(ref, score);
    }

    public void addScore(T subject, double add) {
        Ref key = refs.create(subject);

        addScore(key, add);
    }

    public synchronized double addScore(Ref ref, double add) {
        return scoreMap.computeDouble(ref, (r, score) -> (score != null ? score : 0) + add);
    }

    public double getScore(T subject) {
        return getScore(refs.create(subject));
    }

    public synchronized double getScore(Ref ref) {
        return scoreMap.computeIfAbsent(ref, r -> 0.d);
    }

    @Override
    public synchronized Optional<DataEntry<Ref>> getEntry(Ref ref) {
        double score = scoreMap.getOrDefault(ref, Double.NaN);

        if (Double.isNaN(score)) {
            return Optional.empty();
        }

        return Optional.of(new DoubleScoreDataEntry<>(ref, score, format, detailKey));
    }

    @Override
    public synchronized Stream<DoubleScoreDataEntry<Ref>> streamOrderedEntries() {
        return scoreMap.object2DoubleEntrySet().stream()
                .map(e -> new DoubleScoreDataEntry<>(e.getKey(), e.getDoubleValue(), format, detailKey))
                .sorted(ordering.orderDouble(DoubleScoreDataEntry::score));
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

    public synchronized Optional<Double> getBestScore() {
        return ordering.best(scores());
    }

    public synchronized Optional<Double> getWorstScore() {
        return ordering.opposite().best(scores());
    }

    private synchronized DoubleStream scores() {
        return scoreMap.values().doubleStream();
    }

    public synchronized Set<T> getBestSubjects(SubjectRefResolver<T, Ref> resolver) {
        Optional<Double> best = getBestScore();

        if (best.isEmpty()) return Set.of();

        final double bestScore = best.get();

        return scoreMap.keySet().stream()
                .filter(ref -> abs(scoreMap.getDouble(ref) - bestScore) < 1e-10)
                .map(resolver::resolve)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public synchronized DataContainer<T, Ref> copy() {
        var copy = new DoubleScoreDataContainer<>(refs, ordering, detailKey);
        copy.scoreMap.putAll(this.scoreMap);

        return copy;
    }
}
