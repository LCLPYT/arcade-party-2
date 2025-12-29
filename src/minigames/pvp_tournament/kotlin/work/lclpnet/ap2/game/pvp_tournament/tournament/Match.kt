package work.lclpnet.ap2.game.pvp_tournament.tournament

import work.lclpnet.ap2.impl.game.data.type.PlayerRef

class Match(
    var leftChild: Match? = null,
    var rightChild: Match? = null,
    var leftPlayer: PlayerRef? = null,
    var rightPlayer: PlayerRef? = null,
    var winnerNext: Match? = null,
    var loserNext: Match? = null,
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
    fun acceptPlayerFromChildMatch(player: PlayerRef?, from: Match) {
        when (from) {
            leftChild -> leftPlayer = player
            rightChild -> rightPlayer = player
        }
    }

    fun isBye(): Boolean =
        // no parent, and exactly one winner
        (leftChild == null && rightChild == null && byeMatchWinner() != null)
                // only left child and no right player
                || (leftChild != null && rightChild == null && rightPlayer == null)
                // only right child and no left player
                || (leftChild == null && rightChild != null && leftPlayer == null)

    fun byeMatchChild(): Match? = when {
        leftChild != null && rightChild == null -> leftChild
        leftChild == null && rightChild != null -> rightChild
        else -> null
    }

    fun byeMatchWinner(): PlayerRef? = when {
        leftPlayer != null && rightPlayer == null -> leftPlayer
        leftPlayer == null && rightPlayer != null -> rightPlayer
        else -> null
    }

    fun getChildren(): List<Match> = listOfNotNull(leftChild, rightChild)

    fun isLeaf() =
        leftChild == null && rightChild == null

    fun isFinale() = winnerNext == null
}