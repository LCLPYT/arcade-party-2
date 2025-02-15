package work.lclpnet.ap2.impl.game.item;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import work.lclpnet.kibu.hook.HookRegistrar;

public interface SpecialItem {

    String id();

    ItemStack createItemStack();

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
     * @return The {@link ActionResult} to be forwarded to the interaction hook.
     */
    default ActionResult onUse(ServerPlayerEntity player, ItemStack stack, Hand hand) {
        return ActionResult.PASS;
    }

    default void registerHooks(HookRegistrar hooks, SpecialItemContext ctx) {}
}
