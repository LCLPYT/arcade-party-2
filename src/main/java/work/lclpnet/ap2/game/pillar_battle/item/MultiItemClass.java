package work.lclpnet.ap2.game.pillar_battle.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public record MultiItemClass(List<Item> items) implements ItemClass {

    @Override
    public ItemStack getRandomStack(Random random) {
        Item item = items.get(random.nextInt(items.size()));
        return new ItemStack(item);
    }

    @Nullable
    public static MultiItemClass ofTag(TagKey<Item> tag) {
        List<Item> items = new ArrayList<>();

        for (var entry : Registries.ITEM.iterateEntries(tag)) {
            items.add(entry.value());
        }

        if (items.isEmpty()) {
            return null;
        }

        return new MultiItemClass(items);
    }
}
