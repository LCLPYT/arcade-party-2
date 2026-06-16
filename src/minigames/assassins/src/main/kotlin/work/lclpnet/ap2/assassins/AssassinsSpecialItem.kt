package work.lclpnet.ap2.assassins

import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ext.mc.isOf
import kotlin.random.Random

/**
 * A one-use reward item handed out at the start of the round after a player killed their target.
 */
enum class AssassinsSpecialItem(val id: String, val item: Item) {
    INVISIBILITY("invisibility", Items.FERMENTED_SPIDER_EYE),
    JUMP_BOOST("jump_boost", Items.RABBIT_FOOT),
    REVEAL_ASSASSIN("reveal_assassin", Items.ENDER_EYE);

    val translationKey: String get() = "game.ap2.assassins.item.$id"

    companion object {

        fun random(random: Random): AssassinsSpecialItem = entries[random.nextInt(entries.size)]

        fun byStack(stack: ItemStack): AssassinsSpecialItem? = entries.firstOrNull {
            stack.isOf(it.item)
        }
    }
}
