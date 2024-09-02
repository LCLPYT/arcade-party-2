package work.lclpnet.ap2.game.pillar_battle.item;

import net.minecraft.item.ItemStack;

import java.util.Random;

public interface ItemClass {
    ItemStack getRandomStack(Random random);
}
