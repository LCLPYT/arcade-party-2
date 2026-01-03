package work.lclpnet.ap2.game.pvp_tournament.util

import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.item.alchemy.Potions
import work.lclpnet.ap2.ext.mc.unbreakable

val KITS_1V1: List<Kit> get() = listOf(
    Kit("default").apply {
        set(0, ItemStack(Items.STONE_SWORD).unbreakable())
        set(1, ItemStack(Items.FISHING_ROD).unbreakable())
        set(2, ItemStack(Items.BOW).unbreakable())
        set(3, ItemStack(Items.COBBLESTONE, 32))
        set(5, ItemStack(Items.SPLASH_POTION, 2).apply {
            set(DataComponents.POTION_CONTENTS, PotionContents(Potions.HEALING))
        })
        set(6, ItemStack(Items.GOLDEN_APPLE))
        set(7, ItemStack(Items.COOKED_BEEF, 4))
        set(8, ItemStack(Items.WATER_BUCKET))
        set(35, ItemStack(Items.ARROW, 16))

        set(EquipmentSlot.HEAD, ItemStack(Items.IRON_HELMET))
        set(EquipmentSlot.CHEST, ItemStack(Items.IRON_CHESTPLATE))
        set(EquipmentSlot.LEGS, ItemStack(Items.IRON_LEGGINGS))
        set(EquipmentSlot.FEET, ItemStack(Items.IRON_BOOTS))
    }
)
