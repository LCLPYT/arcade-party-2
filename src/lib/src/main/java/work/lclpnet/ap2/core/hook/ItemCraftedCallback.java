package work.lclpnet.ap2.core.hook;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

public interface ItemCraftedCallback {

    Hook<ItemCraftedCallback> HOOK = HookFactory.createArrayBacked(ItemCraftedCallback.class,
            hooks -> (player, stack, amount) -> {
        for (var hook : hooks) {
            hook.onCrafted(player, stack, amount);
        }
    });

    void onCrafted(ServerPlayer player, ItemStack stack, int amount);
}
