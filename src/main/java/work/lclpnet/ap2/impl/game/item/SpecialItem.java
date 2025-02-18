package work.lclpnet.ap2.impl.game.item;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import work.lclpnet.kibu.hook.HookRegistrar;

public interface SpecialItem {

    String id();

    ItemStack createItemStack(DynamicRegistryManager registryManager);

    /**
     * Creates a new instance of the used item stack.
     * Used for example when dropping special items.
     * The returned {@link ItemStack} should not be localized, but have all state set, such as durability.
     * @param current The current, maybe localized stack from a player's inventory.
     * @param registryManager The {@link DynamicRegistryManager}.
     * @return A newly initialized {@link ItemStack} with the used item state set.
     */
    default ItemStack usedItemStack(ItemStack current, DynamicRegistryManager registryManager) {
        return createItemStack(registryManager);
    }

    default boolean canBeDropped(ServerPlayerEntity player, ItemStack stack) {
        return true;
    }

    /**
     * Called when a player picked up an instance of the special item.
     * @param player The player.
     */
    default void onPickedUp(ServerPlayerEntity player) {}

    /**
     * Called when a player uses (right-clicks) the special item.
     * @param player The player.
     * @param stack The item.
     * @param hand The hand in which the player is holding the item that is being used.
     * @param ctx The context.
     * @return The {@link ActionResult} to be forwarded to the interaction hook.
     */
    default ActionResult onUse(ServerPlayerEntity player, ItemStack stack, Hand hand, SpecialItemContext ctx) {
        return ActionResult.PASS;
    }

    default void registerHooks(HookRegistrar hooks, SpecialItemContext ctx) {}
}
