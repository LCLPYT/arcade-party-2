package work.lclpnet.ap2.impl.base;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.api.base.GameQueue;
import work.lclpnet.ap2.impl.game.TestMiniGame;
import work.lclpnet.ap2.impl.util.VoidQueuePersistence;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VotedGameQueueTest {

    @BeforeAll
    public static void bootstrap() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @Test
    void pollNextGame_noGames_throws() {
        var queue = new VotedGameQueue(Set.of(), List.of(), 5, VoidQueuePersistence.instance());

        assertThrows(IllegalStateException.class, queue::pollNextGame);
    }

    @Test
    void pollNextGame_votedFewerThanMinimum_orderAsExpected() {
        TestMiniGame gameA = new TestMiniGame();
        TestMiniGame gameB = new TestMiniGame();
        TestMiniGame gameC = new TestMiniGame();

        var queue = new VotedGameQueue(Set.of(gameA), List.of(gameC, gameB), 5, VoidQueuePersistence.instance());

        assertEquals(gameC, queue.pollNextGame());
        assertEquals(gameB, queue.pollNextGame());
        assertEquals(gameA, queue.pollNextGame());
        assertEquals(gameA, queue.pollNextGame());
        assertEquals(gameA, queue.pollNextGame());
        assertEquals(gameA, queue.pollNextGame());
    }

    @Test
    void preview_fewerThanMinimum_filledUpToMinimum() {
        TestMiniGame game = new TestMiniGame();

        var queue = new VotedGameQueue(Set.of(game), List.of(), 5, VoidQueuePersistence.instance());

        assertEquals(List.of(game, game, game, game, game), queue.preview().stream()
                .map(GameQueue.Entry::game)
                .limit(5)
                .toList());
    }

    @Test
    void preview_votedFewerThanMinimum_filledUpByGameManager() {
        TestMiniGame gameA = new TestMiniGame();
        TestMiniGame gameB = new TestMiniGame();

        var queue = new VotedGameQueue(Set.of(gameA), List.of(gameB), 5, VoidQueuePersistence.instance());

        assertEquals(List.of(gameB, gameA, gameA, gameA, gameA), queue.preview().stream()
                .map(GameQueue.Entry::game)
                .limit(5)
                .toList());
    }

    @Test
    void shiftGame_otherGames_unmodified() {
        TestMiniGame gameA = new TestMiniGame();
        TestMiniGame gameB = new TestMiniGame();
        TestMiniGame gameC = new TestMiniGame();

        var queue = new VotedGameQueue(Set.of(gameA), List.of(gameB), 5, VoidQueuePersistence.instance());

        queue.setNextGame(gameC);

        assertEquals(List.of(gameC, gameB, gameA, gameA, gameA), queue.preview().stream()
                .map(GameQueue.Entry::game)
                .limit(5)
                .toList());
    }
}