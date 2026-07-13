package work.lclpnet.ap2.task_rush.task

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.data.Ordering
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Base task for "collect the most X" challenges.
 * When the task begins, all matching items are temporarily removed so that every player starts at zero.
 * Players are given feedback whenever a matching item is picked up, and the removed items are handed back
 * through the item queue once the task is over.
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

    private fun removeMatching(player: ServerPlayer): List<ItemStack> {
        val removed = ArrayList<ItemStack>()
        val inventory = player.inventory

        for (i in 0 until inventory.containerSize) {
            val stack = inventory.getItem(i)

            if (!stack.isEmpty && matches(stack)) {
                removed.add(stack.copy())
                inventory.setItem(i, ItemStack.EMPTY)
            }
        }

        return removed
    }

    override fun begin(env: TaskEnv) {
        val removed = HashMap<UUID, List<ItemStack>>()
        val collected = HashMap<UUID, Int>()

        for (player in env.players) {
            removed[player.uuid] = removeMatching(player)
        }

        PlayerInventoryHooks.PLAYER_PICKUP.registerWith(env.hooks) { player, itemEntity ->
            val stack = itemEntity.item

            if (player is ServerPlayer && env.players.isParticipating(player) && !stack.isEmpty && matches(stack)) {
                val total = (collected[player.uuid] ?: 0) + stack.count
                collected[player.uuid] = total

                env.feedback(player, "task.feedback.collect", total, sound = true)
            }

            false
        }

        env.timer("task.$id.task", duration) {
            val data = IntScoreDataContainer(PlayerRef::create, Ordering.DESCENDING, scoreKey)

            for (player in env.players) {
                data.setScore(player, count(player))
                removed[player.uuid]?.forEach { env.give(player, it) }
            }

            env.complete(data)
        }
    }
}
