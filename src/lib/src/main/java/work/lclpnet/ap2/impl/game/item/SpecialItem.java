package work.lclpnet.ap2.impl.game.item;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;

public interface SpecialItem {

    String id();

    ItemStack createItemStack(RegistryAccess registryManager);

    /**
     * Creates a new instance of the used item stack.
     * Used for example when dropping special items.
     * The returned {@link ItemStack} should not be localized, but have all state set, such as durability.
     * @param current The current, maybe localized stack from a player's inventory.
     * @param registryManager The {@link RegistryAccess}.
     * @return A newly initialized {@link ItemStack} with the used item state set.
     */
    default ItemStack usedItemStack(ItemStack current, RegistryAccess registryManager) {
        ItemStack stack = createItemStack(registryManager);

        if (current.has(DataComponents.DAMAGE)) {
            stack.set(DataComponents.DAMAGE, current.get(DataComponents.DAMAGE));
        }

        return stack;
    }

    default boolean canBeDropped(ServerPlayer player, ItemStack stack) {
        return true;
    }

    default boolean canBePickedUp(ServerPlayer player) {
        return true;
    }

    default boolean shouldTransferToInventory(ServerPlayer player) {
        return true;
    }

    /**
     * Called when a player picked up an instance of the special item.
     * @param player The player.
     * @param stack The {@link ItemStack}.
     * @param ctx The context.
     */
    default void onPickedUp(ServerPlayer player, ItemStack stack, SpecialItemContext ctx) {}

    /**
     * Called when a player drops an instance of the special item.
     * @param player The player.
     */
    default void onDropped(ServerPlayer player) {}

    /**
     * Called when a player uses (right-clicks) the special item.
     * @param player The player.
     * @param stack The item.
     * @param hand The hand in which the player is holding the item that is being used. Or null if the item was used otherwise.
     * @param ctx The context.
     * @return The {@link InteractionResult} to be forwarded to the interaction hook.
     */
    default InteractionResult onUse(ServerPlayer player, ItemStack stack, @Nullable InteractionHand hand, SpecialItemContext ctx) {
        return InteractionResult.PASS;
    }

    /**
     * Called when a player swings the special item, usually by left-clicking / attacking.
     * @param player The player.
     * @param stack The item stack.
     * @param hand The hand in which the player is holding the item being swung. Or null if the item was swung otherwise.
     * @param ctx The context.
     */
    default void onSwing(ServerPlayer player, ItemStack stack, @Nullable InteractionHand hand, SpecialItemContext ctx) {}

    default void registerHooks(HookRegistrar hooks, SpecialItemContext ctx) {}

    default void scheduleTasks(TaskScheduler scheduler, SpecialItemContext ctx) {}
}
