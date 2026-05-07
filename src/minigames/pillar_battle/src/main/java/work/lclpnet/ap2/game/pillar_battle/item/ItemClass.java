package work.lclpnet.ap2.game.pillar_battle.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Random;
import java.util.stream.Stream;

public interface ItemClass {

    ItemStack getRandomStack(Random random);

    Stream<Item> stream();
}
