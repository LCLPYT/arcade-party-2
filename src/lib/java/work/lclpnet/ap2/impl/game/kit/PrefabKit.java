package work.lclpnet.ap2.impl.game.kit;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

import static java.lang.Math.min;

public class PrefabKit extends BaseKit {

    private final List<ItemStack> items;

    public PrefabKit(KitHandle handle, String id, List<ItemStack> items) {
        super(handle, id);

        this.items = items;
    }

    @Override
    public ItemStack createItemStack(DynamicRegistryManager manager) {
        if (items.isEmpty()) {
            return new ItemStack(Items.BARRIER);
        }

        return items.getFirst().copyWithCount(1);
    }

    @Override
    public void equip(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        final int len = min(inventory.size(), items.size());

        for (int i = 0; i < len; i++) {
            if (i == KitHandler.KIT_SELECTOR_SLOT) continue;

            ItemStack stack = items.get(i);
            inventory.setStack(i, stack.copy());
        }
    }

    @Override
    public void unequip(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        final int len = min(inventory.size(), items.size());

        for (int i = 0; i < len; i++) {
            if (i == KitHandler.KIT_SELECTOR_SLOT) continue;

            inventory.setStack(i, ItemStack.EMPTY);
        }
    }
}
