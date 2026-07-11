package work.lclpnet.ap2.deadline

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ext.mc.unbreakable
import work.lclpnet.ap2.impl.util.ItemHelper.getLeatherArmor

private val ARMOR = mapOf(
    EquipmentSlot.HEAD to Items.LEATHER_HELMET,
    EquipmentSlot.CHEST to Items.LEATHER_CHESTPLATE,
    EquipmentSlot.LEGS to Items.LEATHER_LEGGINGS,
    EquipmentSlot.FEET to Items.LEATHER_BOOTS,
)

/**
 * The rider's leather armor, dyed to match their sheep and trail.
 */
object RiderOutfit {

    fun equip(player: ServerPlayer, color: DyeColor) {
        val colorInt = color.textureDiffuseColor

        for ((slot, item) in ARMOR) {
            player.setItemSlot(slot, getLeatherArmor(item, colorInt).unbreakable())
        }
    }

    fun unequip(player: ServerPlayer) {
        for (slot in ARMOR.keys) {
            player.setItemSlot(slot, ItemStack.EMPTY)
        }
    }
}
