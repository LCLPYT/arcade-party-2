package work.lclpnet.ap2.game.pvp_tournament.util

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Avatar
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.Mannequin
import net.minecraft.world.item.ItemStack
import work.lclpnet.combatctl.api.CombatStyle
import work.lclpnet.combatctl.impl.CombatStyles

class Kit(
    val id: String,
    val combatStyle: CombatStyle = CombatStyles.CLASSIC,
) {
    private val items = (0..35).map { ItemStack.EMPTY }.toMutableList()
    private val equipment = mutableMapOf<EquipmentSlot, ItemStack>()

    operator fun set(slot: Int, stack: ItemStack) {
        require(slot in 0..35) { "Slot not within 0..35" }

        items[slot] = stack
    }

    operator fun get(slot: Int): ItemStack {
        require(slot in 0..35) { "Slot not within 0..35" }

        return items[slot]
    }

    operator fun set(slot: EquipmentSlot, stack: ItemStack) {
        require(slot.type == EquipmentSlot.Type.HUMANOID_ARMOR || slot == EquipmentSlot.OFFHAND) {
            "Unsupported equipment slot: $slot"
        }

        equipment[slot] = stack
    }

    operator fun get(slot: EquipmentSlot): ItemStack = equipment[slot] ?: ItemStack.EMPTY

    fun equip(player: Avatar) {
        player.setItemSlot(EquipmentSlot.OFFHAND, this[EquipmentSlot.OFFHAND].copy())
        player.setItemSlot(EquipmentSlot.HEAD, this[EquipmentSlot.HEAD].copy())
        player.setItemSlot(EquipmentSlot.CHEST, this[EquipmentSlot.CHEST].copy())
        player.setItemSlot(EquipmentSlot.LEGS, this[EquipmentSlot.LEGS].copy())
        player.setItemSlot(EquipmentSlot.FEET, this[EquipmentSlot.FEET].copy())

        if (player is ServerPlayer) {
            items.forEachIndexed { index, stack ->
                player.inventory.setItem(index, stack.copy())
            }
        }

        if (player is Mannequin) {
            player.setItemSlot(EquipmentSlot.MAINHAND, items[0].copy())
        }
    }
}