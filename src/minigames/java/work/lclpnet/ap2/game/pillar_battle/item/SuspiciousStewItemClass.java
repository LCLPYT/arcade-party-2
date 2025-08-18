package work.lclpnet.ap2.game.pillar_battle.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.SuspiciousStewEffectsComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
        List<SuspiciousStewEffectsComponent.StewEffect> effects = new ArrayList<>(effectCount);

        for (int i = 0; i < effectCount; i++) {
            var potion = ItemHelper.getRandomStatusEffect(random);
            int durationTicks = random.nextInt(Ticks.seconds(1), Ticks.seconds(10));

            effects.add(new SuspiciousStewEffectsComponent.StewEffect(potion, durationTicks));
        }

        stack.set(DataComponentTypes.SUSPICIOUS_STEW_EFFECTS, new SuspiciousStewEffectsComponent(effects));

        return stack;
    }

    @Override
    public Stream<Item> stream() {
        return Stream.of(Items.SUSPICIOUS_STEW);
    }
}
