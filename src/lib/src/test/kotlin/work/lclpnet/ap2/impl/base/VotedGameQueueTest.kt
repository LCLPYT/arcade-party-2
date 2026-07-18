package work.lclpnet.ap2.impl.base

import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import work.lclpnet.ap2.game.MiniGame
import work.lclpnet.ap2.impl.game.TestMiniGame
import work.lclpnet.gaco.ds.queue.VoidQueuePersistence
import java.util.List
import java.util.Set

internal class VotedGameQueueTest {
    @Test
    fun new_noGames_throws() {
        Assertions.assertThrows(IllegalArgumentException::class.java) {
            VotedGameQueue(
                setOf(),
                listOf(),
                5,
                VoidQueuePersistence.instance()
            )
        }
    }

    @Test
    fun pollNextGame_votedFewerThanMinimum_orderAsExpected() {
        val gameA = TestMiniGame()
        val gameB = TestMiniGame()
        val gameC = TestMiniGame()

        val queue = VotedGameQueue(
            setOf(gameA),
            listOf(gameC, gameB),
            5,
            VoidQueuePersistence.instance()
        )

        assertEquals(gameC, queue.pollNextGame())
        assertEquals(gameB, queue.pollNextGame())
        assertEquals(gameA, queue.pollNextGame())
        assertEquals(gameA, queue.pollNextGame())
        assertEquals(gameA, queue.pollNextGame())
        assertEquals(gameA, queue.pollNextGame())
    }

    @Test
    fun preview_fewerThanMinimum_filledUpToMinimum() {
        val game = TestMiniGame()

        val queue = VotedGameQueue(
            setOf(game),
            mutableListOf(),
            5,
            VoidQueuePersistence.instance()
        )

        assertEquals(
            listOf(game, game, game, game, game),
            queue.preview().stream()
                .map(GameQueue.Entry::game)
                .limit(5)
                .toList()
        )
    }

    @Test
    fun preview_votedFewerThanMinimum_filledUpByGameManager() {
        val gameA = TestMiniGame()
        val gameB = TestMiniGame()

        val queue = VotedGameQueue(
            setOf(gameA),
            listOf(gameB),
            5,
            VoidQueuePersistence.instance()
        )

        assertEquals(
            listOf(gameB, gameA, gameA, gameA, gameA),
            queue.preview().stream()
                .map(GameQueue.Entry::game)
                .limit(5)
                .toList()
        )
    }

    @Test
    fun shiftGame_otherGames_unmodified() {
        val gameA = TestMiniGame()
        val gameB = TestMiniGame()
        val gameC = TestMiniGame()

        val queue = VotedGameQueue(
            setOf(gameA),
            listOf(gameB),
            5,
            VoidQueuePersistence.instance()
        )

        queue.setNextGame(gameC)

        assertEquals(
            listOf(gameC, gameB, gameA, gameA, gameA),
            queue.preview().stream()
                .map(GameQueue.Entry::game)
                .limit(5)
                .toList()
        )
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }
}