package work.lclpnet.ap2.capture_the_flag

import net.minecraft.core.RegistryAccess
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.TooltipDisplay
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.item.equipment.trim.ArmorTrim
import net.minecraft.world.item.equipment.trim.TrimMaterials
import net.minecraft.world.item.equipment.trim.TrimPatterns
import work.lclpnet.ap2.ext.mc.enchant
import work.lclpnet.ap2.ext.mc.unbreakable
import work.lclpnet.ap2.game.team.DyeTeamKey
import work.lclpnet.ap2.game.team.TeamKey
import work.lclpnet.ap2.game.team.TeamManager
import work.lclpnet.ap2.impl.util.ItemHelper

private const val ARROW_COUNT = 32
private const val QUICK_CHARGE_LEVEL = 2

private val TRIM_MATERIALS = mapOf(
    DyeTeamKey.RED to TrimMaterials.REDSTONE,
    DyeTeamKey.BLUE to TrimMaterials.LAPIS,
    DyeTeamKey.LIGHT_BLUE to TrimMaterials.DIAMOND,
    DyeTeamKey.DARK_GREEN to TrimMaterials.EMERALD,
    DyeTeamKey.LIME to TrimMaterials.EMERALD,
    DyeTeamKey.YELLOW to TrimMaterials.GOLD,
    DyeTeamKey.ORANGE to TrimMaterials.COPPER,
    DyeTeamKey.PURPLE to TrimMaterials.AMETHYST,
    DyeTeamKey.MAGENTA to TrimMaterials.AMETHYST,
    DyeTeamKey.WHITE to TrimMaterials.QUARTZ,
    DyeTeamKey.BLACK to TrimMaterials.NETHERITE,
    DyeTeamKey.BROWN to TrimMaterials.RESIN,
)

class CtfKit(private val teamManager: TeamManager, level: ServerLevel) {

    private val registryAccess: RegistryAccess = level.registryAccess()

    fun equip(player: ServerPlayer) {
        val inventory = player.inventory

        inventory.clearContent()

        inventory.setItem(0, ItemStack(Items.CROSSBOW).unbreakable()
            .enchant(Enchantments.QUICK_CHARGE, QUICK_CHARGE_LEVEL, registryAccess))
        inventory.setItem(1, ItemStack(Items.STONE_SWORD).unbreakable())
        inventory.setItem(2, ItemStack(Items.ARROW, ARROW_COUNT))

        val trim = armorTrim(teamManager.getTeam(player)?.key)

        player.setItemSlot(EquipmentSlot.HEAD, armorPiece(Items.LEATHER_HELMET, trim))
        player.setItemSlot(EquipmentSlot.CHEST, armorPiece(Items.LEATHER_CHESTPLATE, trim))
        player.setItemSlot(EquipmentSlot.LEGS, armorPiece(Items.LEATHER_LEGGINGS, trim))
        player.setItemSlot(EquipmentSlot.FEET, armorPiece(Items.LEATHER_BOOTS, trim))
    }

    private fun armorTrim(key: TeamKey?): ArmorTrim {
        val materialKey = TRIM_MATERIALS[key] ?: TrimMaterials.IRON

        val material = ItemHelper.getTrimMaterial(registryAccess, materialKey)
        val pattern = registryAccess.lookupOrThrow(Registries.TRIM_PATTERN).getOrThrow(TrimPatterns.SENTRY)

        return ArmorTrim(material, pattern)
    }

    private fun armorPiece(item: Item, trim: ArmorTrim): ItemStack = ItemStack(item).apply {
        unbreakable()

        set(DataComponents.TRIM, trim)
        set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.TRIM, true))
    }
}
