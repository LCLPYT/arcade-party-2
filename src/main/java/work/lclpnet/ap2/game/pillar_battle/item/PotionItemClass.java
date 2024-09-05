package work.lclpnet.ap2.game.pillar_battle.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
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

        stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(potion));

        return stack;
    }

    @Override
    public Stream<Item> stream() {
        return Stream.of(item);
    }
}
