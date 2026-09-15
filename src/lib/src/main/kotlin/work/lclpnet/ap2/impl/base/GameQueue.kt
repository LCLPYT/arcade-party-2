package work.lclpnet.ap2.impl.base

import work.lclpnet.ap2.game.MiniGame

interface GameQueue {
    fun pollNextGame(): MiniGame

    fun preview(): List<Entry>

    fun setNextGame(miniGame: MiniGame)

    fun shiftGame(miniGame: MiniGame)

    fun setFilter(filter: (MiniGame) -> Boolean)

    fun updateHistory(game: MiniGame)

    enum class Type {
        REGULAR,
        VOTED,
        PRIORITY
    }

    data class Entry(
        val game: MiniGame,
        val type: Type,
    )
}
