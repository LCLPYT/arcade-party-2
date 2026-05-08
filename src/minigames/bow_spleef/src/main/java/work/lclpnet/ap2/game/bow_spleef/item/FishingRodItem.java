package work.lclpnet.ap2.game.bow_spleef.item;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
    public ItemStack createItemStack(RegistryAccess registryManager) {
        ItemStack stack = new ItemStack(Items.FISHING_ROD);

        stack.set(DataComponents.MAX_DAMAGE, USES * 3);

        return stack;
    }

    @Override
    public void registerHooks(HookRegistrar hooks, SpecialItemContext ctx) {
        PlayerInventoryHooks.SLOT_CHANGE.registerWith(hooks, (player, _) -> {
            if (!ctx.hasSpecialItem(player, this)
                    || player.fishing == null
                    || player.fishing.isRemoved()
                    || player.fishing.getHookedIn() == null) return;

            player.getInventory().getItem(8).hurtAndBreak(3, player, EquipmentSlot.MAINHAND);
        });
    }
}
