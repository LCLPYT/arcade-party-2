package work.lclpnet.ap2.impl.game.item;

import net.minecraft.item.ItemStack;

public interface SpecialItem {

    String id();

    ItemStack createItemStack();
}
