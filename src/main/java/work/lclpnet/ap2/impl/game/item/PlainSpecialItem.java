package work.lclpnet.ap2.impl.game.item;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.DynamicRegistryManager;

public record PlainSpecialItem(String id, ItemStack stack) implements SpecialItem {

    @Override
    public ItemStack createItemStack(DynamicRegistryManager registryManager) {
        return stack.copy();
    }
}
