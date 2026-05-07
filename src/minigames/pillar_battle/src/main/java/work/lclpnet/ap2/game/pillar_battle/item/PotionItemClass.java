package work.lclpnet.ap2.game.pillar_battle.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import work.lclpnet.ap2.impl.util.ItemHelper;

import java.util.Random;
import java.util.stream.Stream;

public class PotionItemClass implements ItemClass {

    private final Item item;

    public PotionItemClass(Item item) {
        this.item = item;
    }

    @Override
    public ItemStack getRandomStack(Random random) {
        ItemStack stack = new ItemStack(item);

        var potion = ItemHelper.getRandomPotion(random);

        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));

        return stack;
    }

    @Override
    public Stream<Item> stream() {
        return Stream.of(item);
    }
}
