package work.lclpnet.ap2.game.pillar_battle.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.Random;

public record SingletonItemClass(Item item) implements ItemClass {
    @Override
    public ItemStack getRandomStack(Random random) {
        return new ItemStack(item);
    }
}
