package work.lclpnet.ap2.impl.base;

import work.lclpnet.ap2.api.base.GameQueue;
import work.lclpnet.ap2.api.game.MiniGame;
import work.lclpnet.ap2.api.util.QueuePersistence;
import work.lclpnet.ap2.impl.util.SeamlessQueue;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import static java.lang.Math.floor;
import static java.lang.Math.max;

/**
 * A game queue that picks voted games first.
 * After all voted games were played, a secondary (infinite) queue is used.
 * If the next game cannot be played, skips the queue until a playable game is found.
 * When the queue is exhausted, it is complemented by a secondary queue.
 */
public class VotedGameQueue implements GameQueue {

    private static final float MARGIN_PERCENT = 0.4f;

    private final int minimumSize;
    private final SeamlessQueue<MiniGame> regular;
    private final Queue<MiniGame> voted = new LinkedList<>();
    private final LinkedList<MiniGame> priority = new LinkedList<>();
    private final QueuePersistence<MiniGame> persistence;

    public VotedGameQueue(Set<MiniGame> games, Iterable<MiniGame> voted, int minimumSize, QueuePersistence<MiniGame> persistence) {
        this.minimumSize = minimumSize;
        this.persistence = persistence;

        voted.forEach(this.voted::offer);

        int margin = (int) floor(games.size() * MARGIN_PERCENT);
        var transfer = persistence.restore();

        this.regular = new SeamlessQueue<>(games, new Random(), margin, transfer);
        voted.forEach(regular::pushUpcoming);
    }

    @Override
    public synchronized MiniGame pollNextGame() {
        if (!priority.isEmpty()) {
            return priority.poll();
        }

        if (!voted.isEmpty()) {
            return voted.poll();
        }

        return regular.next();
    }

    @Override
    public synchronized List<Entry> preview() {
        int nonRegularSize = priority.size() + voted.size();
        int remainingRegular = max(0, minimumSize - nonRegularSize);

        List<Entry> preview = new ArrayList<>(priority.size() + voted.size() + remainingRegular);
        priority.forEach(game -> preview.add(new Entry(game, Type.PRIORITY)));
        voted.forEach(game -> preview.add(new Entry(game, Type.VOTED)));
        regular.peek(remainingRegular).forEach(game -> preview.add(new Entry(game, Type.REGULAR)));

        return preview;
    }

    @Override
    public synchronized void setNextGame(MiniGame miniGame) {
        Objects.requireNonNull(miniGame);

        priority.clear();
        priority.add(miniGame);
    }

    @Override
    public synchronized void shiftGame(MiniGame miniGame) {
        Objects.requireNonNull(miniGame);

        priority.addFirst(miniGame);
    }

    @Override
    public synchronized void setFilter(Predicate<MiniGame> filter) {
        var invalid = Predicate.not(filter);

        priority.removeIf(invalid);
        voted.removeIf(invalid);

        regular.filter(filter);
    }

    @Override
    public void updateHistory(MiniGame game) {
        regular.pushHistory(game);

        CompletableFuture.runAsync(() -> persistence.store(regular.transfer()));
    }
}
