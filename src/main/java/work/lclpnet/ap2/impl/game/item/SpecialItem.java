package work.lclpnet.ap2.impl.game.item;

import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

public interface SpecialItem {

    String id();

    ItemStack createItemStack();

    /**
     * Called when a player is about to pick up the special item.
     * @param player The player.
     * @param itemEntity The item entity representing an instance of the special item.
     * @return True, if the default special item pickup logic should be cancelled. False otherwise.
     */
    default boolean onPickUp(ServerPlayerEntity player, ItemEntity itemEntity) {
        return false;
    }

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
}
