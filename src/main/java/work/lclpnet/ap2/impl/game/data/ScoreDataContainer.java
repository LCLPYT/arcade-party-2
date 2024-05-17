package work.lclpnet.ap2.impl.game.data;

import work.lclpnet.ap2.api.event.IntScoreEvent;
import work.lclpnet.ap2.api.event.IntScoreEventSource;
import work.lclpnet.ap2.api.game.data.*;
import work.lclpnet.ap2.api.game.sink.IntDataSink;
import work.lclpnet.ap2.impl.game.data.entry.ScoreDataEntry;
import work.lclpnet.ap2.impl.game.data.entry.ScoreView;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A data container that orders the subjects by their integer score.
 * The scores can still be updated after a subject was added.
 * The subject with the highest score is the winner.
 */
public class ScoreDataContainer<T, Ref extends SubjectRef> implements DataContainer<T, Ref>, IntScoreEventSource<T>, IntDataSink<T> {

    private final SubjectRefFactory<T, Ref> refs;
    private final Map<Ref, Integer> scoreMap = new HashMap<>();
    private final List<IntScoreEvent<T>> listeners = new ArrayList<>();

    private boolean frozen = false;

    public ScoreDataContainer(SubjectRefFactory<T, Ref> refs) {
        this.refs = refs;

    }

    public void setScore(T subject, int score) {
        synchronized (this) {
            if (frozen) return;
            scoreMap.put(refs.create(subject), score);
        }

        listeners.forEach(listener -> listener.accept(subject, score));
    }

    @Override
    public void addScore(T subject, int add) {
        int score;

        synchronized (this) {
            if (frozen) return;
            Ref key = refs.create(subject);

            score = scoreMap.computeIfAbsent(key, ref -> 0) + add;
            scoreMap.put(key, score);
        }

        listeners.forEach(listener -> listener.accept(subject, score));
    }

    @Override
    public int getScore(T subject) {
        synchronized (this) {
            return scoreMap.computeIfAbsent(refs.create(subject), ref -> 0);
        }
    }

    @Override
    public void delete(T subject) {
        synchronized (this) {
            if (frozen) return;
            scoreMap.remove(refs.create(subject));
        }
    }

    @Override
    public DataEntry<Ref> getEntry(T subject) {
        Ref ref = refs.create(subject);
        synchronized (this) {
            return new ScoreDataEntry<>(ref, scoreMap.get(ref));
        }
    }

    @Override
    public Stream<? extends DataEntry<Ref>> orderedEntries() {
        return scoreMap.entrySet().stream()
                .map(e -> new ScoreDataEntry<>(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(ScoreView::score).reversed());
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

            scoreMap.clear();
        }
    }

    public OptionalInt getBestScore() {
        return scoreMap.values().stream().mapToInt(i -> i).max();
    }

    public Set<T> getBestSubjects(SubjectRefResolver<T, Ref> resolver) {
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
}
