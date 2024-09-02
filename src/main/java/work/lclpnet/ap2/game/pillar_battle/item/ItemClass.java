package work.lclpnet.ap2.game.pillar_battle.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.Random;
import java.util.stream.Stream;

public interface ItemClass {

    ItemStack getRandomStack(Random random);

    Stream<Item> stream();
}
