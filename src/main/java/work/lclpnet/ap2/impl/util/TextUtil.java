package work.lclpnet.ap2.impl.util;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.trim.ArmorTrimPattern;
import net.minecraft.registry.RegistryKey;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

public class TextUtil {

    public static MutableText getVanillaName(Item item) {
        return Text.translatable(item.getTranslationKey());
    }

    public static MutableText getVanillaName(ItemStack stack) {
        Item item = stack.getItem();
        return Text.translatable(item.getTranslationKey(stack));
    }

    public static MutableText getVanillaName(Block block) {
        return Text.translatable(block.getTranslationKey());
    }

    public static MutableText getVanillaName(BlockState state) {
        return getVanillaName(state.getBlock());
    }

    public static MutableText getVanillaName(EntityType<?> entityType) {
        return Text.translatable(entityType.getTranslationKey());
    }

    public static MutableText getVanillaName(SoundEvent soundEvent) {
        return Text.translatable("subtitles." + soundEvent.getId().getPath());
    }

    public static MutableText getVanillaName(RegistryKey<ArmorTrimPattern> pattern) {
        return Text.translatable(Util.createTranslationKey("trim_pattern", pattern.getValue()));
    }

    private TextUtil() {}
}
