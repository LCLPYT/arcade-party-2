package work.lclpnet.ap2.game.pillar_battle.item

import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import java.util.*
import java.util.stream.Stream

data class SingletonItemClass(val item: Item) : ItemClass {
    override fun getRandomStack(random: Random): ItemStack = ItemStack(item)
    override fun stream(): Stream<Item> = Stream.of(item)
}
