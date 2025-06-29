package work.lclpnet.ap2.impl.game.data;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.event.IntScoreEvent;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.game.data.DataEntry;
import work.lclpnet.ap2.api.game.data.SubjectRef;
import work.lclpnet.ap2.api.game.data.SubjectRefFactory;
import work.lclpnet.ap2.impl.game.data.entry.IntScoreDataEntry;
import work.lclpnet.ap2.impl.game.data.entry.ScoreTimeDataEntry;
import work.lclpnet.ap2.impl.game.data.entry.ScoreView;

import java.util.*;
import java.util.stream.Stream;

import static java.util.Comparator.comparingLong;

/**
 * A score container that orders the subjects by their integer score, like {@link IntScoreDataContainer},
 * but the order in which the scores who reached is saved.
 * In case two subjects have the same score, the subject who reached the score first is ranked higher.
 */
public class ScoreTimeDataContainer<T, Ref extends SubjectRef> extends BaseDataContainer<T, Ref> implements IntDataContainer<T, Ref> {

    private final Object2IntMap<Ref> score = new Object2IntOpenHashMap<>();
    private final Object2LongMap<Ref> lastTransaction = new Object2LongOpenHashMap<>();
    private final List<IntScoreEvent<T>> listeners = new ArrayList<>();
    private final @Nullable String detailKey;
    /** An incrementing transaction counter. Used to determine who got to which score first. */
    private long transaction = 0;

    public ScoreTimeDataContainer(SubjectRefFactory<T, Ref> refs) {
        this(refs, null);
    }

    public ScoreTimeDataContainer(SubjectRefFactory<T, Ref> refs, @Nullable String detailKey) {
        super(refs);

        this.detailKey = detailKey;
    }

    public void setScore(T subject, int score) {
        synchronized (this) {
            Ref ref = refs.create(subject);

            this.score.put(ref, score);

            modified(ref);
        }

        listeners.forEach(listener -> listener.accept(subject, score));
    }

    @Override
    public void addScore(T subject, int add) {
        int score;

        synchronized (this) {
            Ref ref = refs.create(subject);
            score = this.score.compute(ref, (_ref, prev) -> (prev != null ? prev : 0) + add);
            modified(ref);
        }

        listeners.forEach(listener -> listener.accept(subject, score));
    }

    @Override
    public synchronized int getScore(T subject) {
        return score.getOrDefault(refs.create(subject), 0);
    }

    private void modified(Ref ref) {
        lastTransaction.put(ref, transaction++);
    }

    @NotNull
    public synchronized Optional<DataEntry<Ref>> getEntry(Ref ref) {
        int score = this.score.getOrDefault(ref, 0);
        int ranking = getTimedRanking(ref);

        if (ranking == 0) {
            return Optional.of(new IntScoreDataEntry<>(ref, score, detailKey));
        }

        return Optional.of(new ScoreTimeDataEntry<>(ref, score, detailKey, ranking));
    }

    @Override
    public synchronized Stream<? extends DataEntry<Ref>> streamOrderedEntries() {
        return score.object2IntEntrySet().stream()
                .map(e -> {
                    Ref ref = e.getKey();
                    int ranking = getTimedRanking(ref);

                    if (ranking == 0) {
                        return new IntScoreDataEntry<>(ref, e.getIntValue(), detailKey);
                    }

                    return new ScoreTimeDataEntry<>(ref, e.getIntValue(), detailKey, ranking);
                })
                .sorted(Comparator.comparingInt(ScoreView::score).reversed().thenComparingInt(x -> {
                    if (x instanceof ScoreTimeDataEntry<?> scoreTime) {
                        return scoreTime.ranking();
                    }

                    return 0;
                }));
    }

    /**
     * Get the ranking among subjects with the same score.
     *
     * @param ref The subject reference.
     * @return The ranking, or 0 if the score is unique.
     */
    private synchronized int getTimedRanking(Ref ref) {
        int subjectScore = score.getInt(ref);

        var ordered = score.object2IntEntrySet().stream()
                .filter(e -> e.getIntValue() == subjectScore)
                .map(Map.Entry::getKey)
                .sorted(comparingLong(lastTransaction::getLong))
                .toList();

        if (ordered.size() <= 1) {
            return 0;
        }

        int rank = 0;

        for (var r : ordered) {
            rank++;

            if (ref.equals(r)) break;
        }

        return rank;
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
        score.clear();
        lastTransaction.clear();
    }

    @Override
    public void register(IntScoreEvent<T> listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    @Override
    public synchronized DataContainer<T, Ref> copy() {
        var copy = new ScoreTimeDataContainer<>(refs);
        copy.transaction = transaction;
        copy.score.putAll(this.score);
        copy.lastTransaction.putAll(this.lastTransaction);

        return copy;
    }
}
