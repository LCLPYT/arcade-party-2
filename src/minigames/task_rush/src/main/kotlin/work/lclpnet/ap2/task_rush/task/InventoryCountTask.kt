package work.lclpnet.ap2.task_rush.task

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.data.Ordering
import work.lclpnet.ap2.game.data.type.PlayerRef
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Base task for "collect the most X" challenges.
 * Snapshots each player's matching item count when the task begins and scores the amount gained during the task, so items carried over from earlier tasks do not count.
 */
abstract class InventoryCountTask(
    override val id: String,
    private val scoreKey: String,
    private val duration: Duration = 45.seconds,
) : Task {

    protected abstract fun matches(stack: ItemStack): Boolean

    private fun count(player: ServerPlayer): Int {
        var total = 0
        val inventory = player.inventory

        for (i in 0 until inventory.containerSize) {
            val stack = inventory.getItem(i)

            if (!stack.isEmpty && matches(stack)) {
                total += stack.count
            }
        }

        return total
    }

    override fun begin(env: TaskEnv) {
        val startCounts = HashMap<UUID, Int>()

        for (player in env.players) {
            startCounts[player.uuid] = count(player)
        }

        env.timer("task.$id.task", duration) {
            val data = IntScoreDataContainer(PlayerRef::create, Ordering.DESCENDING, scoreKey)

            for (player in env.players) {
                val start = startCounts[player.uuid] ?: 0
                val gained = (count(player) - start).coerceAtLeast(0)
                data.setScore(player, gained)
            }

            env.complete(data)
        }
    }
}
