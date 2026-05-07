package work.lclpnet.ap2.game.pillar_battle.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

public record MultiItemClass(List<Item> items) implements ItemClass {

    @Override
    public ItemStack getRandomStack(Random random) {
        Item item = items.get(random.nextInt(items.size()));
        return new ItemStack(item);
    }

    @Nullable
    public static MultiItemClass ofTag(TagKey<Item> tag) {
        List<Item> items = new ArrayList<>();

        for (var entry : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
            items.add(entry.value());
        }

        if (items.isEmpty()) {
            return null;
        }

        return new MultiItemClass(items);
    }

    @Override
    public Stream<Item> stream() {
        return items.stream();
    }
}
