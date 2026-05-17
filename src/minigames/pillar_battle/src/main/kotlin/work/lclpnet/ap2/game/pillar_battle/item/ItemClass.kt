package work.lclpnet.ap2.game.pillar_battle.item

import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import java.util.*
import java.util.stream.Stream

interface ItemClass {
    fun getRandomStack(random: Random): ItemStack
    fun stream(): Stream<Item>
}
