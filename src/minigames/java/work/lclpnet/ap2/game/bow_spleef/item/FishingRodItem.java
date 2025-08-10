package work.lclpnet.ap2.game.bow_spleef.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import work.lclpnet.ap2.impl.game.item.SpecialItem;
import work.lclpnet.ap2.impl.game.item.SpecialItemContext;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks;

public class FishingRodItem implements SpecialItem {

    private static final int USES = 3;

    @Override
    public String id() {
        return "fishing_rod";
    }

    @Override
    public ItemStack createItemStack(DynamicRegistryManager registryManager) {
        ItemStack stack = new ItemStack(Items.FISHING_ROD);

        stack.set(DataComponentTypes.MAX_DAMAGE, USES * 3);

        return stack;
    }

    @Override
    public void registerHooks(HookRegistrar hooks, SpecialItemContext ctx) {
        hooks.registerHook(PlayerInventoryHooks.SLOT_CHANGE, (player, i) -> {
            if (!ctx.hasSpecialItem(player, this)
                    || player.fishHook == null
                    || player.fishHook.isRemoved()
                    || player.fishHook.getHookedEntity() == null) return;

            player.getInventory().getStack(8).damage(3, player, EquipmentSlot.MAINHAND);
        });
    }
}
