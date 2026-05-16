package work.lclpnet.ap2.game.cozy_campfire.setup

import net.minecraft.core.Holder
import net.minecraft.core.RegistryAccess
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.TooltipDisplay
import net.minecraft.world.item.equipment.trim.ArmorTrim
import net.minecraft.world.item.equipment.trim.TrimMaterials
import net.minecraft.world.item.equipment.trim.TrimPattern
import work.lclpnet.ap2.api.game.team.TeamManager
import work.lclpnet.ap2.game.cozy_campfire.TEAM_RED
import work.lclpnet.ap2.impl.util.ItemHelper
import work.lclpnet.ap2.impl.util.ItemHelper.unbreakable
import java.util.Random
import java.util.UUID

class CCKitManager(
    private val teamManager: TeamManager,
    private val world: ServerLevel,
    private val random: Random
) {

    private val patterns = mutableMapOf<UUID, Holder<TrimPattern>>()

    fun giveItems(player: ServerPlayer) {
        val inventory = player.inventory

        inventory.setItem(0, unbreakable(ItemStack(Items.IRON_SWORD)))
        inventory.setItem(1, unbreakable(ItemStack(Items.IRON_PICKAXE)))
        inventory.setItem(2, unbreakable(ItemStack(Items.IRON_AXE)))
        inventory.setItem(3, unbreakable(ItemStack(Items.IRON_SHOVEL)))
        inventory.setItem(4, unbreakable(ItemStack(Items.IRON_HOE)))

        val registryManager: RegistryAccess = world.registryAccess()
        val trimPattern = patterns.getOrPut(player.uuid) { ItemHelper.getRandomTrimPattern(registryManager, random) }
        val trimMaterialKey = teamManager.getTeam(player)
            .map { if (it.key() == TEAM_RED) TrimMaterials.REDSTONE else TrimMaterials.LAPIS }
            .orElse(TrimMaterials.IRON)
        val trimMaterial = ItemHelper.getTrimMaterial(registryManager, trimMaterialKey)

        fun armorPiece(item: net.minecraft.world.item.Item): ItemStack {
            val stack = unbreakable(ItemStack(item))
            stack.set(DataComponents.TRIM, ArmorTrim(trimMaterial, trimPattern))
            stack.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.TRIM, true))
            return stack
        }

        player.setItemSlot(EquipmentSlot.HEAD, armorPiece(Items.IRON_HELMET))
        player.setItemSlot(EquipmentSlot.CHEST, armorPiece(Items.IRON_CHESTPLATE))
        player.setItemSlot(EquipmentSlot.LEGS, armorPiece(Items.IRON_LEGGINGS))
        player.setItemSlot(EquipmentSlot.FEET, armorPiece(Items.IRON_BOOTS))
    }
}
