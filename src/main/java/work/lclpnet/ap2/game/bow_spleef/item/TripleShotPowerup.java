package work.lclpnet.ap2.game.bow_spleef.item;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import work.lclpnet.ap2.impl.game.item.SpecialItem;

public class TripleShotPowerup implements SpecialItem {

    @Override
    public String id() {
        return "triple_shot";
    }

    @Override
    public ItemStack createItemStack() {
        return new ItemStack(Items.FIRE_CHARGE);
    }
}
