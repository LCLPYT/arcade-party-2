package work.lclpnet.ap2.game.pillar_battle.item

import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.SuspiciousStewEffects
import work.lclpnet.ap2.impl.util.ItemHelper
import work.lclpnet.kibu.scheduler.Ticks
import java.util.*
import java.util.stream.Stream

class SuspiciousStewItemClass : ItemClass {
    override fun getRandomStack(random: Random): ItemStack {
        val stack = ItemStack(Items.SUSPICIOUS_STEW)
        val effectCount = random.nextInt(1, 5)
        val effects = ArrayList<SuspiciousStewEffects.Entry>(effectCount)

        repeat(effectCount) {
            val potion = ItemHelper.getRandomStatusEffect(random) ?: return@repeat
            val durationTicks = random.nextInt(Ticks.seconds(1), Ticks.seconds(10))
            effects.add(SuspiciousStewEffects.Entry(potion, durationTicks))
        }

        stack.set(DataComponents.SUSPICIOUS_STEW_EFFECTS, SuspiciousStewEffects(effects))
        return stack
    }

    override fun stream(): Stream<Item> = Stream.of(Items.SUSPICIOUS_STEW)
}
