package work.lclpnet.ap2.game.pvp_tournament.gen

import work.lclpnet.ap2.game.data.type.PlayerRef

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

    val completedAsDraw: Boolean
        get() = completed && winner == null

    @Synchronized
    fun complete(winner: PlayerRef?): Boolean {
        if (completed) return false

        completed = true

        val resolvedWinner = if (leftPlayer == winner || rightPlayer == winner) winner else null

        this.winner = resolvedWinner

        val loser = when (resolvedWinner) {
            leftPlayer -> rightPlayer
            rightPlayer -> leftPlayer
            else -> null
        }

        winnerNext?.let {
            it.acceptPlayerFromChildMatch(resolvedWinner, this)
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

        val leftChild = leftChild
        val rightChild = rightChild

        if (leftChild != null && leftChild.completedAsDraw && rightChild != null && rightChild.completedAsDraw) {
            complete(null)
            return
        }

        val winner = byeMatchWinner() ?: return

        complete(winner)
    }

    fun isBye(): Boolean {
        fun incoming(child: Match?) =
            child != null && !child.completedAsDraw

        return when {
            incoming(leftChild) && incoming(rightChild) -> false
            // only left child and no right player
            incoming(leftChild) -> rightPlayer == null
            // only right child and no left player
            incoming(rightChild) -> leftPlayer == null
            // no children and not two players
            else -> leftPlayer == null || rightPlayer == null
        }
    }

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

    fun other(ref: PlayerRef): PlayerRef? = when(ref) {
        leftPlayer -> rightPlayer
        rightPlayer -> leftPlayer
        else -> null
    }

    /**
     * Creates a copy of this match, but without copying related matches.
     */
    fun shallowCopy(): Match {
        val match = Match(
            round = round,
            leftPlayer = leftPlayer,
            rightPlayer = rightPlayer,
        )

        match.winner = winner
        match.completed = completed

        return match
    }

    override fun toString(): String {
        return "Match(round=$round, players=${players.map { it.name }})"
    }
}