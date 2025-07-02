package work.lclpnet.ap2.impl.game.kit;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;

public class SingleItemKit extends BaseKit {

    private final Item item;
    private final int count;

    protected SingleItemKit(KitHandle handle, String id, Item item, int count) {
        super(handle, id);
        this.item = item;
        this.count = count;
    }

    @Override
    public ItemStack createItemStack(DynamicRegistryManager manager) {
        return new ItemStack(item);
    }

    @Override
    public void equip(ServerPlayerEntity player) {
        ItemStack stack = handle.createItemStack(this, player);
        stack.setCount(count);

        player.getInventory().setStack(KitHandler.KIT_ITEM_SLOT, stack);
    }

    @Override
    public void unequip(ServerPlayerEntity player) {
        player.getInventory().removeStack(KitHandler.KIT_ITEM_SLOT);
    }
}
