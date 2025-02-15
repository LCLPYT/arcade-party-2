package work.lclpnet.ap2.impl.game.item;

import net.minecraft.item.ItemStack;

public record PlainSpecialItem(String id, ItemStack stack) implements SpecialItem {

    @Override
    public ItemStack createItemStack() {
        return stack.copy();
    }
}
