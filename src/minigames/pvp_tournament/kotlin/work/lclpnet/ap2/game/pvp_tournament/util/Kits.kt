package work.lclpnet.ap2.game.pvp_tournament.util

import net.minecraft.core.RegistryAccess
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.item.alchemy.Potions
import net.minecraft.world.item.enchantment.Enchantments
import work.lclpnet.ap2.ext.mc.enchant
import work.lclpnet.ap2.ext.mc.unbreakable
import work.lclpnet.gaco.ds.WeightedList

fun getKits(registryAccess: RegistryAccess): WeightedList<Kit> {
    val commonKits = WeightedList<Kit>().apply {
        add(Kit("default").apply {
            set(0, ItemStack(Items.STONE_SWORD).unbreakable())
            set(1, ItemStack(Items.FISHING_ROD).unbreakable())
            set(2, ItemStack(Items.GOLDEN_APPLE))
            set(3, ItemStack(Items.COBBLESTONE, 32))

            set(7, ItemStack(Items.COOKED_BEEF, 4))
            set(8, ItemStack(Items.BOW).unbreakable())

            set(35, ItemStack(Items.ARROW, 10))

            set(EquipmentSlot.HEAD, ItemStack(Items.IRON_HELMET).unbreakable())
            set(EquipmentSlot.CHEST, ItemStack(Items.DIAMOND_CHESTPLATE).unbreakable())
            set(EquipmentSlot.LEGS, ItemStack(Items.DIAMOND_LEGGINGS).unbreakable())
            set(EquipmentSlot.FEET, ItemStack(Items.IRON_BOOTS).unbreakable())
        }, 1f)

        add(Kit("copper").apply {
            set(0, ItemStack(Items.IRON_SWORD).unbreakable())
            set(1, ItemStack(Items.COBBLESTONE, 32))
            set(2, ItemStack(Items.GOLDEN_APPLE))

            set(EquipmentSlot.HEAD, ItemStack(Items.IRON_HELMET).unbreakable())
            set(EquipmentSlot.CHEST, ItemStack(Items.COPPER_CHESTPLATE).unbreakable())
            set(EquipmentSlot.LEGS, ItemStack(Items.COPPER_LEGGINGS).unbreakable())
            set(EquipmentSlot.FEET, ItemStack(Items.IRON_BOOTS).unbreakable())
        }, 1f)

        add(Kit("soup").apply {
            set(0, ItemStack(Items.WOODEN_SWORD).unbreakable())
            set(1, ItemStack(Items.FISHING_ROD).unbreakable())

            (2..8).forEach {
                set(it, ItemStack(Items.MUSHROOM_STEW))
            }

            set(EquipmentSlot.HEAD, ItemStack(Items.LEATHER_HELMET).unbreakable())
            set(EquipmentSlot.CHEST, ItemStack(Items.CHAINMAIL_CHESTPLATE).unbreakable())
            set(EquipmentSlot.LEGS, ItemStack(Items.LEATHER_LEGGINGS).unbreakable())
            set(EquipmentSlot.FEET, ItemStack(Items.LEATHER_BOOTS).unbreakable())
        }, 0.6f)
    }

    val uncommonKits = WeightedList<Kit>().apply {
        add(Kit("trident").apply {
            set(0, ItemStack(Items.TRIDENT)
                .unbreakable()
                .enchant(Enchantments.LOYALTY, 3, registryAccess)
                .enchant(Enchantments.IMPALING, 3, registryAccess)
            )

            set(1, ItemStack(Items.SPLASH_POTION).apply {
                set(DataComponents.POTION_CONTENTS, PotionContents(Potions.STRONG_HEALING))
            })

            set(2, ItemStack(Items.SPLASH_POTION).apply {
                set(DataComponents.POTION_CONTENTS, PotionContents(Potions.STRONG_HEALING))
            })

            set(8, ItemStack(Items.PRISMARINE, 32))

            set(EquipmentSlot.HEAD, ItemStack(Items.GOLDEN_HELMET).unbreakable())
            set(EquipmentSlot.CHEST, ItemStack(Items.GOLDEN_CHESTPLATE).unbreakable())
            set(EquipmentSlot.LEGS, ItemStack(Items.GOLDEN_LEGGINGS).unbreakable())
            set(EquipmentSlot.FEET, ItemStack(Items.GOLDEN_BOOTS).unbreakable())
        }, 0.75f)

        add(Kit("mace").apply {
            set(0, ItemStack(Items.MACE)
                .unbreakable()
            )

            set(1, ItemStack(Items.WIND_CHARGE, 32))

            set(8, ItemStack(Items.COBWEB, 8))

            set(EquipmentSlot.HEAD, ItemStack(Items.NETHERITE_HELMET).unbreakable())
            set(EquipmentSlot.CHEST, ItemStack(Items.NETHERITE_CHESTPLATE).unbreakable())
            set(EquipmentSlot.LEGS, ItemStack(Items.NETHERITE_LEGGINGS).unbreakable())
            set(EquipmentSlot.FEET, ItemStack(Items.NETHERITE_BOOTS)
                .unbreakable()
                .enchant(Enchantments.FEATHER_FALLING, 4, registryAccess)
            )
        }, 0.5f)

        add(Kit("spear").apply {
            set(0, ItemStack(Items.DIAMOND_SPEAR).unbreakable())
            set(1, ItemStack(Items.CROSSBOW).unbreakable())
            set(2, ItemStack(Items.COBBLESTONE, 32))

            (4..5).forEach {
                set(it, ItemStack(Items.SPLASH_POTION).apply {
                    set(DataComponents.POTION_CONTENTS, PotionContents(Potions.STRONG_HARMING))
                })
            }

            set(8, ItemStack(Items.POTION).apply {
                set(DataComponents.POTION_CONTENTS, PotionContents(Potions.STRONG_HEALING))
            })

            set(35, ItemStack(Items.TIPPED_ARROW, 16).apply {
                set(DataComponents.POTION_CONTENTS, PotionContents(Potions.WEAKNESS))
                set(DataComponents.POTION_DURATION_SCALE, 0.125F)
            })

            set(EquipmentSlot.HEAD, ItemStack(Items.DIAMOND_HELMET).unbreakable())
            set(EquipmentSlot.CHEST, ItemStack(Items.NETHERITE_CHESTPLATE).unbreakable())
            set(EquipmentSlot.LEGS, ItemStack(Items.DIAMOND_LEGGINGS).unbreakable())
            set(EquipmentSlot.FEET, ItemStack(Items.DIAMOND_BOOTS).unbreakable())
        }, 0.75f)
    }

    val commonWeight = 0.7f
    val uncommonChance = 0.3f

    return WeightedList<Kit>().apply {
        addAll(commonKits.normalized().scaleWeights(commonWeight))
        addAll(uncommonKits.normalized().scaleWeights(uncommonChance))
    }
}