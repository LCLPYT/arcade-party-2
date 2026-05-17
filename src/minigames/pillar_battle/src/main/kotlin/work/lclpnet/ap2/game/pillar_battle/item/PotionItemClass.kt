package work.lclpnet.ap2.game.pillar_battle.item

import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.alchemy.PotionContents
import work.lclpnet.ap2.impl.util.ItemHelper
import java.util.*
import java.util.stream.Stream

class PotionItemClass(private val item: Item) : ItemClass {
    override fun getRandomStack(random: Random): ItemStack {
        val stack = ItemStack(item)
        stack.set(DataComponents.POTION_CONTENTS, PotionContents(ItemHelper.getRandomPotion(random)))
        return stack
    }

    override fun stream(): Stream<Item> = Stream.of(item)
}
