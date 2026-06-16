package work.lclpnet.ap2.assassins

import net.minecraft.server.level.ServerPlayer
import java.util.*
import kotlin.random.Random

/**
 * Tracks the per-round target/hunter assignment.
 * Targets form a single random cycle, so no two players hunt each other, except when there are only two players.
 */
class AssassinTargets {

    private val target = HashMap<UUID, ServerPlayer>()
    private val hunter = HashMap<UUID, ServerPlayer>()

    fun assign(players: List<ServerPlayer>, random: Random) {
        target.clear()
        hunter.clear()

        if (players.size < 2) return

        val order = players.toMutableList()

        // Fisher-Yates shuffle with the game random
        for (i in order.indices.reversed()) {
            val j = random.nextInt(i + 1)
            val tmp = order[i]
            order[i] = order[j]
            order[j] = tmp
        }

        for (i in order.indices) {
            val a = order[i]
            val b = order[(i + 1) % order.size]

            target[a.uuid] = b
            hunter[b.uuid] = a
        }
    }

    fun targetOf(player: ServerPlayer): ServerPlayer? = target[player.uuid]

    fun hunterOf(player: ServerPlayer): ServerPlayer? = hunter[player.uuid]

    fun remove(player: ServerPlayer) {
        target.remove(player.uuid)
        hunter.remove(player.uuid)
    }

    fun clear() {
        target.clear()
        hunter.clear()
    }
}
