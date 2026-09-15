package work.lclpnet.ap2.impl.base

import work.lclpnet.ap2.game.MiniGame
import work.lclpnet.gaco.ds.queue.QueuePersistence
import work.lclpnet.gaco.ds.queue.SeamlessQueue
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.math.floor
import kotlin.math.max

/**
 * A game queue that picks voted games first.
 * After all voted games were played, a secondary (infinite) queue is used.
 * If the next game cannot be played, skips the queue until a playable game is found.
 * When the queue is exhausted, it is complemented by a secondary queue.
 */
class VotedGameQueue(
    games: Set<MiniGame>,
    voted: Iterable<MiniGame>,
    private val minimumSize: Int,
    private val persistence: QueuePersistence<MiniGame>,
) : GameQueue {
    private val regular: SeamlessQueue<MiniGame>
    private val voted: Queue<MiniGame> = LinkedList()
    private val priority = LinkedList<MiniGame>()

    init {
        for (miniGame in voted) {
            this.voted.offer(miniGame)
        }

        val margin = floor((games.size * MARGIN_PERCENT).toDouble()).toInt()
        val transfer = persistence.restore()

        this.regular = SeamlessQueue(games, Random(), margin, transfer)

        for (miniGame in voted) {
            regular.pushUpcoming(miniGame)
        }
    }

    @Synchronized
    override fun pollNextGame(): MiniGame {
        if (!priority.isEmpty()) {
            return priority.poll()
        }

        if (!voted.isEmpty()) {
            return voted.poll()
        }

        return regular.next()
    }

    @Synchronized
    override fun preview(): List<GameQueue.Entry> {
        val nonRegularSize = priority.size + voted.size
        val remainingRegular = max(0, minimumSize - nonRegularSize)

        val preview = mutableListOf<GameQueue.Entry>()

        for (game in priority) {
            preview.add(GameQueue.Entry(game, GameQueue.Type.PRIORITY))
        }

        for (game in voted) {
            preview.add(GameQueue.Entry(game, GameQueue.Type.VOTED))
        }

        for (game in regular.peek(remainingRegular)) {
            preview.add(GameQueue.Entry(game, GameQueue.Type.REGULAR))
        }

        return preview
    }

    @Synchronized
    override fun setNextGame(miniGame: MiniGame) {
        priority.clear()
        priority.add(miniGame)
    }

    @Synchronized
    override fun shiftGame(miniGame: MiniGame) {
        priority.addFirst(miniGame)
    }

    @Synchronized
    public override fun setFilter(filter: (MiniGame) -> Boolean) {
        val invalid = { miniGame: MiniGame -> !filter(miniGame) }

        priority.removeIf(invalid)
        voted.removeIf(invalid)

        regular.filter(filter)
    }

    override fun updateHistory(game: MiniGame) {
        regular.pushElement(game)

        CompletableFuture.runAsync {
            persistence.store(regular.transfer())
        }
    }

    companion object {
        private const val MARGIN_PERCENT = 0.4f
    }
}
