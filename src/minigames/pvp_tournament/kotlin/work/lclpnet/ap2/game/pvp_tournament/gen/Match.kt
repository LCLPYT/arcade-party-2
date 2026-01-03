package work.lclpnet.ap2.game.pvp_tournament.gen

import work.lclpnet.ap2.impl.game.data.type.PlayerRef

class Match(
    var round: Int,
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

    val players: List<PlayerRef>
        get() = listOfNotNull(leftPlayer, rightPlayer)

    @Synchronized
    fun complete(winner: PlayerRef?): Boolean {
        if (completed) return false

        completed = true
        this.winner = winner

        val loser = when (winner) {
            leftPlayer -> rightPlayer
            rightPlayer -> leftPlayer
            else -> null
        }

        winnerNext?.let {
            it.acceptPlayerFromChildMatch(winner, this)
            it.propagateByeMatch()
        }

        loserNext?.let {
            it.acceptPlayerFromChildMatch(loser, this)
            it.propagateByeMatch()
        }

        return true
    }

    @Synchronized
    fun acceptPlayerFromChildMatch(player: PlayerRef?, from: Match) {
        when (from) {
            leftChild -> leftPlayer = player
            rightChild -> rightPlayer = player
        }
    }

    @Synchronized
    fun propagateByeMatch() {
        if (!isBye()) return

        val winner = byeMatchWinner() ?: return

        complete(winner)
    }

    fun isBye(): Boolean =
        // no children and exactly one winner
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

    fun hasPlayer(ref: PlayerRef) =
        leftPlayer == ref || rightPlayer == ref

    fun participant(ref: PlayerRef): Int = when (ref) {
        leftPlayer -> 0
        rightPlayer -> 1
        else -> -1
    }
}