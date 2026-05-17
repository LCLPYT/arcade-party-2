package work.lclpnet.ap2.game.pillar_battle.item

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import java.util.*
import java.util.stream.Stream

data class MultiItemClass(val items: List<Item>) : ItemClass {
    override fun getRandomStack(random: Random): ItemStack = ItemStack(items[random.nextInt(items.size)])
    override fun stream(): Stream<Item> = items.stream()

    companion object {
        @JvmStatic
        fun ofTag(tag: TagKey<Item>): MultiItemClass? {
            val items = mutableListOf<Item>()
            for (entry in BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                items.add(entry.value())
            }
            return if (items.isEmpty()) null else MultiItemClass(items)
        }
    }
}
