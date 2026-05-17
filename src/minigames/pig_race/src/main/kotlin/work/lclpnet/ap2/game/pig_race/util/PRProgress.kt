package work.lclpnet.ap2.game.pig_race.util

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.api.game.MiniGameHandle
import java.util.*

class PRProgress(
    private val gameHandle: MiniGameHandle,
    val path: SegmentedPath,
    val rounds: Int
) {

    private val playerRounds = Object2IntOpenHashMap<UUID>()
    private val ranking = ArrayList<Entry>()

    fun update() {
        val newRanking = ArrayList<Entry>()

        for (player in gameHandle.participants) {
            newRanking.add(Entry(player.uuid, getAbsoluteDistance(player)))
        }

        newRanking.sortByDescending { it.distance }

        synchronized(this) {
            ranking.clear()
            ranking.addAll(newRanking)
        }
    }

    @Synchronized
    fun getFurthestAbsoluteDistance(): Double = if (ranking.isEmpty()) 0.0 else ranking.first().distance

    @Synchronized
    fun getRanking(): List<ServerPlayer> = ranking.stream()
        .map { it.uuid }
        .map { gameHandle.participants.getParticipant(it) }
        .flatMap { it.stream() }
        .toList()

    fun getAbsoluteRemaining(player: ServerPlayer): Double {
        val remaining = 1 - getAbsoluteProgress(player)
        return remaining * path.combinedLength * rounds
    }

    fun getAbsoluteDistance(player: ServerPlayer): Double =
        getAbsoluteProgress(player) * path.combinedLength * rounds

    fun getAbsoluteProgress(player: ServerPlayer): Double {
        val round = getRound(player)
        val progress = path.getProgress(player)
        return rounds.toDouble().coerceIn(0.0, (round - 1) + progress) / rounds
    }

    fun getRound(player: ServerPlayer): Int = playerRounds.getOrDefault(player.uuid, 1)

    fun incrementRound(player: ServerPlayer) {
        playerRounds.put(player.uuid, getRound(player) + 1)
    }

    private data class Entry(val uuid: UUID, val distance: Double)
}
