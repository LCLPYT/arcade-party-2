package work.lclpnet.ap2.deadline.rider

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ext.mc.unbreakable
import work.lclpnet.ap2.impl.util.ItemHelper.getLeatherArmor

/**
 * The rider's leather armor, dyed to match their sheep and trail.
 */
object RiderOutfit {

    fun equip(player: ServerPlayer, color: DyeColor) {
        val colorInt = color.textureDiffuseColor

        player.setItemSlot(EquipmentSlot.HEAD, getLeatherArmor(Items.LEATHER_HELMET, colorInt).unbreakable())
        player.setItemSlot(EquipmentSlot.CHEST, getLeatherArmor(Items.LEATHER_CHESTPLATE, colorInt).unbreakable())
        player.setItemSlot(EquipmentSlot.LEGS, getLeatherArmor(Items.LEATHER_LEGGINGS, colorInt).unbreakable())
        player.setItemSlot(EquipmentSlot.FEET, getLeatherArmor(Items.LEATHER_BOOTS, colorInt).unbreakable())
    }

    fun unequip(player: ServerPlayer) {
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY)
        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY)
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY)
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY)
    }
}
