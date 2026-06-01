package work.lclpnet.ap2.impl.game.kit;

import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

import static java.lang.Math.min;

public class PrefabKit extends BaseKit {

    private final List<ItemStack> items;

    public PrefabKit(KitHandle handle, String id, List<ItemStack> items) {
        super(handle, id);

        this.items = items;
    }

    @Override
    public ItemStack createItemStack(RegistryAccess manager) {
        if (items.isEmpty()) {
            return new ItemStack(Items.BARRIER);
        }

        return items.getFirst().copyWithCount(1);
    }

    @Override
    public void equip(ServerPlayer player, KitOptions options) {
        Inventory inventory = player.getInventory();
        final int len = min(inventory.getContainerSize(), items.size());

        for (int i = 0; i < len; i++) {
            if (i == options.kitSelectorSlot()) continue;

            ItemStack stack = items.get(i);
            inventory.setItem(i, stack.copy());
        }
    }

    @Override
    public void unequip(ServerPlayer player, KitOptions options) {
        Inventory inventory = player.getInventory();
        final int len = min(inventory.getContainerSize(), items.size());

        for (int i = 0; i < len; i++) {
            if (i == options.kitSelectorSlot()) continue;

            inventory.setItem(i, ItemStack.EMPTY);
        }
    }
}
