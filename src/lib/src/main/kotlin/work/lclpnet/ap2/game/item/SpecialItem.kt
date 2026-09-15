package work.lclpnet.ap2.game.item

import net.minecraft.core.RegistryAccess
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.scheduler.api.TaskScheduler

interface SpecialItem {

    val id: String

    fun createItemStack(registryManager: RegistryAccess): ItemStack

    /**
     * Creates a new instance of the used item stack.
     * Used for example when dropping special items.
     * The returned [ItemStack] should not be localized, but have all state set, such as durability.
     * @param current The current, maybe localized stack from a player's inventory.
     * @param registryManager The [RegistryAccess].
     * @return A newly initialized [ItemStack] with the used item state set.
     */
    fun usedItemStack(current: ItemStack, registryManager: RegistryAccess): ItemStack {
        val stack = createItemStack(registryManager)

        if (current.has(DataComponents.DAMAGE)) {
            stack.set(DataComponents.DAMAGE, current.get(DataComponents.DAMAGE))
        }

        return stack
    }

    fun canBeDropped(player: ServerPlayer, stack: ItemStack): Boolean {
        return true
    }

    fun canBePickedUp(player: ServerPlayer): Boolean {
        return true
    }

    fun shouldTransferToInventory(player: ServerPlayer): Boolean {
        return true
    }

    /**
     * Called when a player picked up an instance of the special item.
     * @param player The player.
     * @param stack The [ItemStack].
     * @param ctx The context.
     */
    fun onPickedUp(player: ServerPlayer, stack: ItemStack, ctx: SpecialItemContext) {}

    /**
     * Called when a player drops an instance of the special item.
     * @param player The player.
     */
    fun onDropped(player: ServerPlayer) {}

    /**
     * Called when a player uses (right-clicks) the special item.
     * @param player The player.
     * @param stack The item.
     * @param hand The hand in which the player is holding the item that is being used. Or null if the item was used otherwise.
     * @param ctx The context.
     * @return The [InteractionResult] to be forwarded to the interaction hook.
     */
    fun onUse(
        player: ServerPlayer,
        stack: ItemStack,
        hand: InteractionHand?,
        ctx: SpecialItemContext,
    ): InteractionResult {
        return InteractionResult.PASS
    }

    /**
     * Called when a player swings the special item, usually by left-clicking / attacking.
     * @param player The player.
     * @param stack The item stack.
     * @param hand The hand in which the player is holding the item being swung. Or null if the item was swung otherwise.
     * @param ctx The context.
     */
    fun onSwing(
        player: ServerPlayer,
        stack: ItemStack,
        hand: InteractionHand?,
        ctx: SpecialItemContext,
    ) {}

    fun registerHooks(hooks: HookRegistrar, ctx: SpecialItemContext) {}

    fun scheduleTasks(scheduler: TaskScheduler, ctx: SpecialItemContext) {}
}
