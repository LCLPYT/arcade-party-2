package work.lclpnet.ap2.mode_default.util

import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.players.PlayerList
import work.lclpnet.ap2.game.data.DataEntry
import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.ap2.game.player.PlayerRankView
import work.lclpnet.kibu.hook.Hook
import work.lclpnet.kibu.hook.HookFactory
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.math.max

class ScoreManager(
    private val playerManager: PlayerList,
    val targetScore: Int
) : PlayerRankView {
    private val data = IntScoreDataContainer(PlayerRef::create)
    val onChange: Hook<Runnable> = HookFactory.createArrayBacked(Runnable::class.java) { hooks ->
        Runnable {
            for (hook in hooks) {
                hook.run()
            }
        }
    }

    var round = 0
        private set

    fun setScore(player: PlayerRef, score: Int) {
        data.setScore(player, max(0, score))

        onChange.invoker().run()
    }

    fun addScore(player: PlayerRef, score: Int) {
        if (score <= 0) return

        data.addScore(player, score)

        onChange.invoker().run()
    }

    fun getScore(ref: PlayerRef): Int =
        data.getScore(ref)

    fun incrementRound() {
        round++
    }

    fun decrementRound() {
        round = max(0, round - 1)
    }

    fun iterateRankedScores(): Iterable<Pair<PlayerRef, Int>> =
        Iterable { data.rankedEntries }

    fun streamEntriesRanked(): Stream<Set<Pair<PlayerRef, Int>>> =
        data.streamEntriesRanked()

    fun hasScores(): Boolean =
        !data.isEmpty

    /**
     * Get players with the best score, if the best score is at least the target score.
     * Resulting references may refer to offline players, who left the game early.
     * @return A stream of references to winners
     */
    val winningPlayers: Set<PlayerRef>
        get() {
            val bestScore = data.bestScore ?: return emptySet()

            if (bestScore < targetScore) return emptySet()

            return data.streamOrderedEntries()
                .filter { entry -> entry.score == bestScore }
                .map { it.subject}
                .collect(Collectors.toSet())
        }

    val finalists: Set<ServerPlayer>
        get() = winningPlayers
            .map { it.uuid }
            .mapNotNull { uuid -> playerManager.getPlayer(uuid) }
            .toSet()

    /**
     * Gets the final winner, if one exists.
     * If there is exactly one player with the highest score of at least the target score, that player is returned.
     * That winning player may be offline.
     * If there are multiple players with the highest score of at least the target score,
     * there will be no final winner yet, unless only one of them is online.
     * In that case that player will be the final winner.
     * @return The final winner, if one exists.
     */
    val finalWinner: PlayerRef?
        get() {
            val winners = winningPlayers

            // there is exactly one winning player, may also possibly be offline right now
            if (winners.size == 1) {
                return winners.iterator().next()
            }

            // check if there is exactly one online winning player among the possibly offline winners
            val onlineWinners = finalists

            if (onlineWinners.size == 1) {
                return PlayerRef.create(onlineWinners.iterator().next())
            }

            return null
        }

    /**
     * Checks if there is exactly one winner who should win the party.
     * The winning player may be offline.
     * If there are multiple winners, but only one is online, that player will be the final winner.
     * @return Whether
     */
    fun hasClearWinner(): Boolean =
        finalWinner != null

    /**
     * @return Whether there are multiple online winners who have to participate in a finale to determine the final winner.
     */
    fun hasMultipleWinners(): Boolean =
        finalists.count() >= 2

    fun getEntry(ref: PlayerRef): DataEntry<PlayerRef>? =
        data.getEntry(ref)

    override fun score(player: ServerPlayer): Int =
        getScore(PlayerRef.create(player))

    override fun rank(player: ServerPlayer): Int {
        val ref = PlayerRef.create(player)

        val ranked: List<Set<Pair<PlayerRef, Int>>> = streamEntriesRanked().collect(Collectors.toList())

        if (ranked.isEmpty()) {
            // no entries yet, everyone is first
            return 1
        }

        // find ranking group of the player and return the rank of it
        val rank = ranked
            .filter { group: Set<Pair<PlayerRef, Int>> -> group.any { (groupMemberRef, _) -> groupMemberRef == ref } }
            .map { group: Set<Pair<PlayerRef, Int>> -> group.first().component2() }
            .firstOrNull()

        if (rank != null) {
            return rank
        }

        // no score for the player yet, but there are other scores
        val worstGroup = ranked.maxBy { set -> set.maxOf { (_, rank) -> rank } }
        val worstRank = worstGroup.maxOf { (_, rank) -> rank }

        // if the worst rank consists has zero points, return that rank, otherwise one rank worse
        val worstScore = worstGroup.minOf { (ref, _) -> getScore(ref) }

        return if (worstScore == 0) worstRank else worstRank + 1
    }
}