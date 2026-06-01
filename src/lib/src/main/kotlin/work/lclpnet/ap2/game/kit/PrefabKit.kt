package work.lclpnet.ap2.game.kit

import net.minecraft.core.RegistryAccess
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import kotlin.math.min

class PrefabKit(handle: KitHandle, id: String, private val items: List<ItemStack>) : BaseKit(handle, id) {

    override fun createItemStack(manager: RegistryAccess): ItemStack {
        if (items.isEmpty()) {
            return ItemStack(Items.BARRIER)
        }

        return items.first().copyWithCount(1)
    }

    override fun equip(player: ServerPlayer, options: KitOptions) {
        val inventory = player.inventory
        val len = min(inventory.containerSize, items.size)

        for (i in 0..<len) {
            if (i == options.kitSelectorSlot) continue

            val stack = items[i]
            inventory.setItem(i, stack.copy())
        }
    }

    override fun unequip(player: ServerPlayer, options: KitOptions) {
        val inventory = player.inventory
        val len = min(inventory.containerSize, items.size)

        for (i in 0..<len) {
            if (i == options.kitSelectorSlot) continue

            inventory.setItem(i, ItemStack.EMPTY)
        }
    }
}
