package work.lclpnet.ap2.impl.util;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class TextUtil {

    public static MutableComponent getVanillaName(Item item) {
        return Component.translatable(item.getDescriptionId());
    }

    public static MutableComponent getVanillaName(ItemStack stack) {
        return stack.getHoverName().copy();
    }

    public static MutableComponent getVanillaName(Block block) {
        return Component.translatable(block.getDescriptionId());
    }

    public static MutableComponent getVanillaName(BlockState state) {
        return getVanillaName(state.getBlock());
    }

    public static MutableComponent getVanillaName(EntityType<?> entityType) {
        return Component.translatable(entityType.getDescriptionId());
    }

    public static MutableComponent getVanillaName(SoundEvent soundEvent) {
        return Component.translatable("subtitles." + soundEvent.location().getPath());
    }

    public static MutableComponent getVanillaName(Holder<TrimPattern> pattern) {
        Identifier id = pattern.unwrapKey().map(ResourceKey::identifier).orElse(null);
        return Component.translatable(Util.makeDescriptionId("trim_pattern", id));
    }

    private TextUtil() {}
}
