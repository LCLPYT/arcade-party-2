package work.lclpnet.ap2.impl.base;

import org.junit.jupiter.api.Test;
import work.lclpnet.ap2.api.base.MiniGameManager;
import work.lclpnet.ap2.impl.game.TestMiniGame;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VotedGameQueueTest {

    @Test
    void pollNextGame_noGames_throws() {
        MiniGameManager manager = mock();

        when(manager.getGames())
                .thenReturn(Set.of());

        var queue = new VotedGameQueue(manager, List.of(), 5);

        assertThrows(IllegalStateException.class, queue::pollNextGame);
    }

    @Test
    void pollNextGame_votedFewerThanMinimum_orderAsExpected() {
        MiniGameManager manager = mock();

        TestMiniGame gameA = new TestMiniGame();
        TestMiniGame gameB = new TestMiniGame();
        TestMiniGame gameC = new TestMiniGame();

        when(manager.getGames())
                .thenReturn(Set.of(gameA));

        var queue = new VotedGameQueue(manager, List.of(gameC, gameB), 5);

        assertEquals(gameC, queue.pollNextGame());
        assertEquals(gameB, queue.pollNextGame());
        assertEquals(gameA, queue.pollNextGame());
        assertEquals(gameA, queue.pollNextGame());
        assertEquals(gameA, queue.pollNextGame());
        assertEquals(gameA, queue.pollNextGame());
    }

    @Test
    void preview_fewerThanMinimum_filledUpToMinimum() {
        MiniGameManager manager = mock();

        TestMiniGame game = new TestMiniGame();

        when(manager.getGames())
                .thenReturn(Set.of(game));

        var queue = new VotedGameQueue(manager, List.of(), 5);

        assertEquals(List.of(game, game, game, game, game), queue.preview().stream().limit(5).toList());
    }

    @Test
    void preview_votedFewerThanMinimum_filledUpByGameManager() {
        MiniGameManager manager = mock();

        TestMiniGame gameA = new TestMiniGame();
        TestMiniGame gameB = new TestMiniGame();

        when(manager.getGames())
                .thenReturn(Set.of(gameA));

        var queue = new VotedGameQueue(manager, List.of(gameB), 5);

        assertEquals(List.of(gameB, gameA, gameA, gameA, gameA), queue.preview().stream().limit(5).toList());
    }

    @Test
    void shiftGame_otherGames_unmodified() {
        MiniGameManager manager = mock();

        TestMiniGame gameA = new TestMiniGame();
        TestMiniGame gameB = new TestMiniGame();
        TestMiniGame gameC = new TestMiniGame();

        when(manager.getGames())
                .thenReturn(Set.of(gameA));

        var queue = new VotedGameQueue(manager, List.of(gameB), 5);

        queue.shiftGame(gameC);

        assertEquals(List.of(gameC, gameB, gameA, gameA, gameA), queue.preview().stream().limit(5).toList());
    }
}