package work.lclpnet.ap2.game.pillar_battle.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import work.lclpnet.ap2.impl.util.ItemHelper;
import work.lclpnet.kibu.scheduler.Ticks;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

public class SuspiciousStewItemClass implements ItemClass {

    @Override
    public ItemStack getRandomStack(Random random) {
        var stack = new ItemStack(Items.SUSPICIOUS_STEW);

        final int effectCount = random.nextInt(1, 5);
        List<SuspiciousStewEffects.Entry> effects = new ArrayList<>(effectCount);

        for (int i = 0; i < effectCount; i++) {
            var potion = ItemHelper.getRandomStatusEffect(random);
            int durationTicks = random.nextInt(Ticks.seconds(1), Ticks.seconds(10));

            effects.add(new SuspiciousStewEffects.Entry(potion, durationTicks));
        }

        stack.set(DataComponents.SUSPICIOUS_STEW_EFFECTS, new SuspiciousStewEffects(effects));

        return stack;
    }

    @Override
    public Stream<Item> stream() {
        return Stream.of(Items.SUSPICIOUS_STEW);
    }
}
