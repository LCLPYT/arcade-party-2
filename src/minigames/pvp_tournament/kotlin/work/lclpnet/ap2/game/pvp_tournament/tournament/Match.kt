package work.lclpnet.ap2.game.pvp_tournament.tournament

import work.lclpnet.ap2.impl.game.data.type.PlayerRef

class Match(
    val leftChild: Match? = null,
    val rightChild: Match? = null,
    var leftPlayer: PlayerRef? = null,
    var rightPlayer: PlayerRef? = null,
    var winnerNext: Match? = null,
    var looserNext: Match? = null,
) {
    var winner: PlayerRef? = null
        private set

    var completed: Boolean = false
        private set

    @Synchronized
    fun complete(winner: PlayerRef?) {
        if (completed) return

        this.winner = winner
        completed = true
    }

    @Synchronized
    fun acceptPlayerFromChildMatch(player: PlayerRef, from: Match) {
        when (from) {
            leftChild -> leftPlayer = player
            rightChild -> rightPlayer = player
        }
    }
}