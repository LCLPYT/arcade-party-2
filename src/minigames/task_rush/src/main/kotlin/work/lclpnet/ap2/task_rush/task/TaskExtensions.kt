package work.lclpnet.ap2.task_rush.task

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

/**
 * Adds the stack to the player's inventory, dropping any part that does not fit at the player's feet.
 */
internal fun giveOrDrop(player: ServerPlayer, stack: ItemStack) {
    player.inventory.add(stack)

    if (!stack.isEmpty) {
        player.drop(stack, false)
    }
}
