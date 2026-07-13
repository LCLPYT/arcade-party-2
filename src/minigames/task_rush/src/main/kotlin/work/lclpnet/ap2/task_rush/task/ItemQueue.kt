package work.lclpnet.ap2.task_rush.task

import net.minecraft.ChatFormatting
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import work.lclpnet.kibu.scheduler.api.TaskScheduler
import work.lclpnet.kibu.translate.Translations
import work.lclpnet.kibu.translate.text.FormatWrapper
import java.util.UUID
import kotlin.collections.ArrayDeque
import kotlin.collections.HashMap
import kotlin.collections.any
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.emptyList
import kotlin.collections.getOrPut
import kotlin.collections.isNotEmpty
import kotlin.collections.iterator
import kotlin.collections.sumOf

/**
 * Game-scoped item delivery.
 * Items are added to a player's inventory immediately when there is space.
 * Anything that does not fit is held and delivered later once space frees up.
 * Players are periodically reminded about the items that are still waiting.
 */
class ItemQueue(
    private val scheduler: TaskScheduler,
    private val server: MinecraftServer,
    private val translations: Translations,
) {

    private val pending = HashMap<UUID, ArrayDeque<ItemStack>>()

    fun init() {
        scheduler.interval(20L) { -> deliverAll() }
        scheduler.interval(200L) { -> notifyPending() }
    }

    fun give(player: ServerPlayer, stack: ItemStack) {
        if (stack.isEmpty) return

        player.inventory.add(stack)

        if (!stack.isEmpty) {
            pending.getOrPut(player.uuid) { ArrayDeque() }.add(stack)
        }
    }

    fun contains(player: ServerPlayer, searchStack: ItemStack): Boolean {
        val items = pending[player.uuid] ?: emptyList()

        return items.any { stack ->
            !stack.isEmpty && ItemStack.isSameItemSameComponents(stack, searchStack)
        }
    }

    private fun deliverAll() {
        if (pending.isEmpty()) return

        val iterator = pending.entries.iterator()

        while (iterator.hasNext()) {
            val entry = iterator.next()
            val player = server.playerList.getPlayer(entry.key) ?: continue
            val queue = entry.value

            while (queue.isNotEmpty()) {
                val stack = queue.first()
                player.inventory.add(stack)

                if (stack.isEmpty) {
                    queue.removeFirst()
                } else {
                    break
                }
            }

            if (queue.isEmpty()) {
                iterator.remove()
            }
        }
    }

    private fun notifyPending() {
        for ((uuid, queue) in pending) {
            if (queue.isEmpty()) continue

            val player = server.playerList.getPlayer(uuid) ?: continue
            val total = queue.sumOf { it.count }

            translations.translateText("itemqueue.full", FormatWrapper.styled(total, ChatFormatting.YELLOW))
                .withStyle(ChatFormatting.RED)
                .sendTo(player)
        }
    }
}
