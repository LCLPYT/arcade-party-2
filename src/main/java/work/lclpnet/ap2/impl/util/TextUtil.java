package work.lclpnet.ap2.impl.util;

import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public class TextUtil {

    public static MutableText getVanillaName(Item item) {
        return Text.translatable(item.getTranslationKey());
    }

    public static MutableText getVanillaName(ItemStack stack) {
        Item item = stack.getItem();
        return Text.translatable(item.getTranslationKey(stack));
    }

    public static MutableText getVanillaName(EntityType<?> entityType) {
        return Text.translatable(entityType.getTranslationKey());
    }

    public static MutableText getVanillaName(SoundEvent soundEvent) {
        return Text.translatable("subtitles." + soundEvent.getId().getPath());
    }

    private TextUtil() {}
}
