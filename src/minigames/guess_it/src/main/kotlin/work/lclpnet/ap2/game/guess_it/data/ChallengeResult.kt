package work.lclpnet.ap2.game.guess_it.data

import net.minecraft.server.level.ServerPlayer
import java.util.*
import kotlin.math.abs

class ChallengeResult {
    private val pointsGained = mutableMapOf<UUID, Int>()
    var correctAnswer: Any? = null

    fun grant(player: ServerPlayer, points: Int) {
        pointsGained[player.getUUID()] = points
    }

    fun getPointsGained(player: ServerPlayer): Int =
        pointsGained.getOrDefault(player.getUUID(), 0)

    fun clear() {
        pointsGained.clear()
        this.correctAnswer = null
    }

    fun grantIfCorrect(
        participants: Iterable<ServerPlayer>,
        correctResult: Int,
        choiceFunction: (ServerPlayer) -> Int?
    ) {
        for (player in participants) {
            val i = choiceFunction(player) ?: continue

            // 3 points, if the answer is correct
            if (i == correctResult) {
                grant(player, 3)
            }
        }
    }

    fun grantClosest3(
        participants: Collection<ServerPlayer>,
        correctResult: Int,
        valueFunction: (ServerPlayer) -> Int?
    ) {
        grantClosest3Diff(participants) { player ->
            val value = valueFunction(player) ?: return@grantClosest3Diff null

            abs(correctResult - value)
        }
    }

    fun grantClosest3Diff(
        participants: Collection<ServerPlayer>,
        diffFunction: (ServerPlayer) -> Int?
    ) {
        val absPlayerDiff = HashMap<ServerPlayer, Int>(participants.size)

        // collect absolute difference to correct result for every player
        for (player in participants) {
            val diff = diffFunction(player) ?: continue

            absPlayerDiff[player] = diff
        }

        // group by difference, sort by least off, select best 3
        val ordered = absPlayerDiff.entries
            .groupBy { it.value }
            .entries
            .sortedBy { it.key }
            .take(3)
            .toList()

        // grant best 3 groups points based on their collective difference
        for (i in ordered.indices) {
            val playerEntries = ordered[i].value

            val points = 3 - i

            for (playerEntry in playerEntries) {
                val player = playerEntry.key

                grant(player, points)
            }
        }
    }
}
