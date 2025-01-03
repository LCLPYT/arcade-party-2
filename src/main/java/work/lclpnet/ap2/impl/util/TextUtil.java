package work.lclpnet.ap2.impl.util;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.equipment.trim.ArmorTrimPattern;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

public class TextUtil {

    public static MutableText getVanillaName(Item item) {
        return item.getName().copy();
    }

    public static MutableText getVanillaName(ItemStack stack) {
        return stack.getName().copy();
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
        return Text.translatable("subtitles." + soundEvent.id().getPath());
    }

    public static MutableText getVanillaName(RegistryEntry<ArmorTrimPattern> pattern) {
        Identifier id = pattern.getKey().map(RegistryKey::getValue).orElse(null);
        return Text.translatable(Util.createTranslationKey("trim_pattern", id));
    }

    private TextUtil() {}
}
