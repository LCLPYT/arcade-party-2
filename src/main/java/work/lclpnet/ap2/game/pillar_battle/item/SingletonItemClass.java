package work.lclpnet.ap2.game.pillar_battle.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.Random;
import java.util.stream.Stream;

public record SingletonItemClass(Item item) implements ItemClass {

    @Override
    public ItemStack getRandomStack(Random random) {
        return new ItemStack(item);
    }

    @Override
    public Stream<Item> stream() {
        return Stream.of(item);
    }
}
