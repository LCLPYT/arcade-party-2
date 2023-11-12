package work.lclpnet.ap2.impl.util;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public class TextUtil {

    public static MutableText getVanillaName(Item item) {
        return Text.translatable(item.getTranslationKey());
    }

    public static MutableText getVanillaName(ItemStack stack) {
        return getVanillaName(stack.getItem());
    }

    private TextUtil() {}
}
