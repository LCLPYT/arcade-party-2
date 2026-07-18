package work.lclpnet.ap2.game

import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.ap2.impl.util.RankUtil

class MiniGameResults(
    val status: Status,
    private val entries: Map<PlayerRef, PlayerResult>
) {
    fun getEntries(): Set<PlayerResult> =
        entries.values.toSet()

    val entriesByRank: List<Set<PlayerResult>>
        get() = RankUtil.rank(
            { entries.values.stream() },
            { it.rank }
        ).toList()

    class PlayerResult(
        val ref: PlayerRef,
        val rank: Int
    ) {
        var coinsAcquired: Int = 0
            set(value) {
                field = value.coerceAtLeast(0)
            }
    }

    enum class Status {
        SUCCESS,
        CANCELLED
    }

    companion object {
        @JvmField
        val EMPTY: MiniGameResults = MiniGameResults(Status.CANCELLED, mapOf())
    }
}
