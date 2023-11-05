package work.lclpnet.ap2.impl.base;

import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.GameQueue;
import work.lclpnet.ap2.api.base.MiniGameManager;
import work.lclpnet.ap2.api.game.MiniGame;

import java.util.*;

/**
 * A game queue that picks voted games first.
 * After all voted games were played, a secondary (infinite) queue is used.
 * If the next game cannot be played, skips the queue until a playable game is found.
 * When the queue is exhausted, it is complemented by a secondary queue.
 */
public class VotedGameQueue implements GameQueue {

    private final MiniGameManager gameManager;
    private final int minimumSize;
    private final Queue<MiniGame> voted = new LinkedList<>(), regular = new LinkedList<>();
    @Nullable
    private MiniGame forcedNextGame = null;
    private final LinkedList<MiniGame> priority = new LinkedList<>();

    public VotedGameQueue(MiniGameManager gameManager, Iterable<MiniGame> voted, int minimumSize) {
        this.gameManager = gameManager;
        this.minimumSize = minimumSize;

        voted.forEach(this.voted::offer);
    }

    @Override
    public MiniGame pollNextGame() {
        synchronized (this) {
            ensureMinimumSize();

            if (forcedNextGame != null) {
                MiniGame next = forcedNextGame;
                forcedNextGame = null;

                return next;
            }

            if (!priority.isEmpty()) {
                return priority.poll();
            }

            if (!voted.isEmpty()) {
                return voted.poll();
            }

            return regular.poll();
        }
    }

    @Override
    public List<MiniGame> preview() {
        synchronized (this) {
            ensureMinimumSize();

            List<MiniGame> preview = new ArrayList<>(priority);
            preview.addAll(voted);
            preview.addAll(regular);

            return preview;
        }
    }

    @Override
    public void setNextGame(MiniGame miniGame) {
        Objects.requireNonNull(miniGame);

        synchronized (this) {
            priority.clear();
            priority.add(miniGame);
        }
    }

    @Override
    public void shiftGame(MiniGame miniGame) {
        Objects.requireNonNull(miniGame);

        synchronized (this) {
            priority.addFirst(miniGame);
        }
    }

    private void ensureMinimumSize() {
        if (size() >= minimumSize) return;

        restockRegular();
    }

    private void restockRegular() {
        var games = gameManager.getGames();

        if (games.isEmpty()) {
            throw new IllegalStateException("There are no games registered");
        }

        while (size() < minimumSize) {
            offerRandomized(games);
        }
    }

    /**
     * Offer a randomized batch of games to the regular queue.
     * @param games The games to randomize
     */
    private void offerRandomized(Set<? extends MiniGame> games) {
        List<MiniGame> batch = new ArrayList<>(games);

        Collections.shuffle(batch);

        batch.forEach(regular::offer);
    }

    private int size() {
        return voted.size() + regular.size();
    }
}
