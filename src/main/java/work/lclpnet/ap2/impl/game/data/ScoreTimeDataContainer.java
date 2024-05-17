package work.lclpnet.ap2.impl.game.data;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.event.IntScoreEvent;
import work.lclpnet.ap2.api.event.IntScoreEventSource;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.game.data.DataEntry;
import work.lclpnet.ap2.api.game.data.SubjectRef;
import work.lclpnet.ap2.api.game.data.SubjectRefFactory;
import work.lclpnet.ap2.api.game.sink.IntDataSink;
import work.lclpnet.ap2.impl.game.data.entry.ScoreDataEntry;
import work.lclpnet.ap2.impl.game.data.entry.ScoreTimeDataEntry;
import work.lclpnet.ap2.impl.game.data.entry.ScoreView;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A score container that orders the subjects by their integer score, like {@link ScoreDataContainer},
 * but the order in which the scores who reached is saved.
 * In case two subjects have the same score, the subject who reached the score first is ranked higher.
 */
public class ScoreTimeDataContainer<T, Ref extends SubjectRef> implements DataContainer<T, Ref>, IntScoreEventSource<T>, IntDataSink<T> {

    private final SubjectRefFactory<T, Ref> refs;
    private final Object2IntMap<Ref> score = new Object2IntOpenHashMap<>();
    private final LinkedHashSet<Ref> lastModified = new LinkedHashSet<>();
    private final List<IntScoreEvent<T>> listeners = new ArrayList<>();

    private boolean frozen = false;

    public ScoreTimeDataContainer(SubjectRefFactory<T, Ref> refs) {
        this.refs = refs;
    }

    public void setScore(T subject, int score) {
        synchronized (this) {
            if (frozen) return;
            this.score.put(refs.create(subject), score);
            touchInternal(subject);
        }

        listeners.forEach(listener -> listener.accept(subject, score));
    }

    @Override
    public void addScore(T subject, int add) {
        int score;

        synchronized (this) {
            if (frozen) return;
            Ref key = refs.create(subject);
            score = this.score.computeIfAbsent(key, ref -> 0) + add;
            this.score.put(key, score);
            touchInternal(subject);
        }

        listeners.forEach(listener -> listener.accept(subject, score));
    }

    @Override
    public int getScore(T subject) {
        synchronized (this) {
            return score.computeIfAbsent(refs.create(subject), ref -> 0);
        }
    }

    // requires external synchronization
    private void touchInternal(T subject) {
        Ref ref = refs.create(subject);

        lastModified.remove(ref);
        lastModified.add(ref);
    }

    @Override
    public void delete(T subject) {
        synchronized (this) {
            if (frozen) return;
            score.removeInt(refs.create(subject));
        }
    }

    @Override
    public DataEntry<Ref> getEntry(T subject) {
        Ref ref = refs.create(subject);

        return getEntry0(ref);
    }

    @NotNull
    private DataEntry<Ref> getEntry0(Ref ref) {
        synchronized (this) {
            int score = this.score.getInt(ref);
            int ranking = getTimedRanking0(ref);

            if (ranking == 0) {
                return new ScoreDataEntry<>(ref, score);
            }

            return new ScoreTimeDataEntry<>(ref, score, ranking);
        }
    }

    @Override
    public Stream<? extends DataEntry<Ref>> orderedEntries() {
        return score.object2IntEntrySet().stream()
                .map(e -> {
                    Ref ref = e.getKey();
                    int ranking = getTimedRanking(ref);

                    if (ranking == 0) {
                        return new ScoreDataEntry<>(ref, e.getIntValue());
                    }

                    return new ScoreTimeDataEntry<>(ref, e.getIntValue(), ranking);
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
     * @param ref The subject reference.
     * @return The ranking, or 0 if the score is unique.
     */
    private int getTimedRanking(Ref ref) {
        synchronized (this) {
            return getTimedRanking0(ref);
        }
    }

    // requires external synchronization
    private int getTimedRanking0(Ref ref) {
        int subjectScore = score.getInt(ref);

        var sameScore = score.object2IntEntrySet().stream()
                .filter(e -> e.getIntValue() == subjectScore)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        if (sameScore.size() <= 1) {
            // there is only one subject who has this score
            return 0;
        }

        int rank = 1;

        for (Ref other : lastModified) {
            if (!sameScore.contains(other)) continue;

            if (other.equals(ref)) break;

            rank++;
        }

        return rank;
    }

    @Override
    public void freeze() {
        synchronized (this) {
            frozen = true;
        }
    }

    @Override
    public void ensureTracked(T subject) {
        addScore(subject, 0);
    }

    @Override
    public void clear() {
        synchronized (this) {
            if (frozen) return;

            score.clear();
            lastModified.clear();
        }
    }

    @Override
    public void register(IntScoreEvent<T> listener) {
        listeners.add(Objects.requireNonNull(listener));
    }
}
