package work.lclpnet.ap2.impl.game.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

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
        ItemStack stack = createItemStack(registryManager);

        if (current.contains(DataComponentTypes.DAMAGE)) {
            stack.set(DataComponentTypes.DAMAGE, current.get(DataComponentTypes.DAMAGE));
        }

        return stack;
    }

    default boolean canBeDropped(ServerPlayerEntity player, ItemStack stack) {
        return true;
    }

    default boolean canBePickedUp(ServerPlayerEntity player) {
        return true;
    }

    default boolean shouldTransferToInventory(ServerPlayerEntity player) {
        return true;
    }

    /**
     * Called when a player picked up an instance of the special item.
     * @param player The player.
     * @param stack The {@link ItemStack}.
     * @param ctx The context.
     */
    default void onPickedUp(ServerPlayerEntity player, ItemStack stack, SpecialItemContext ctx) {}

    /**
     * Called when a player drops an instance of the special item.
     * @param player The player.
     */
    default void onDropped(ServerPlayerEntity player) {}

    /**
     * Called when a player uses (right-clicks) the special item.
     * @param player The player.
     * @param stack The item.
     * @param hand The hand in which the player is holding the item that is being used. Or null if the item was used otherwise.
     * @param ctx The context.
     * @return The {@link ActionResult} to be forwarded to the interaction hook.
     */
    default ActionResult onUse(ServerPlayerEntity player, ItemStack stack, @Nullable Hand hand, SpecialItemContext ctx) {
        return ActionResult.PASS;
    }

    /**
     * Called when a player swings the special item, usually by left-clicking / attacking.
     * @param player The player.
     * @param stack The item stack.
     * @param hand The hand in which the player is holding the item being swung. Or null if the item was swung otherwise.
     * @param ctx The context.
     */
    default void onSwing(ServerPlayerEntity player, ItemStack stack, @Nullable Hand hand, SpecialItemContext ctx) {}

    default void registerHooks(HookRegistrar hooks, SpecialItemContext ctx) {}

    default void scheduleTasks(TaskScheduler scheduler, SpecialItemContext ctx) {}
}
