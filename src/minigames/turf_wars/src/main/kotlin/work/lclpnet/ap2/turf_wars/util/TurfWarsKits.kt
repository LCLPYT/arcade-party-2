package work.lclpnet.ap2.turf_wars.util

import net.minecraft.core.RegistryAccess
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ext.mc.unbreakable
import work.lclpnet.ap2.game.kit.BaseKit
import work.lclpnet.ap2.game.kit.KitHandle
import work.lclpnet.ap2.game.kit.KitOptions

class ArcherKit(handle: KitHandle) : BaseKit(handle, "archer") {
    override fun createItemStack(manager: RegistryAccess): ItemStack = ItemStack(Items.BOW)

    override fun equip(player: ServerPlayer, options: KitOptions) {
        player.inventory.setItem(0, ItemStack(Items.BOW).unbreakable())
    }

    override fun unequip(player: ServerPlayer, options: KitOptions) {
        player.inventory.setItem(0, ItemStack.EMPTY)
    }
}

class AssassinKit(handle: KitHandle) : BaseKit(handle, "assassin") {
    override fun createItemStack(manager: RegistryAccess): ItemStack = ItemStack(Items.IRON_SWORD)

    override fun equip(player: ServerPlayer, options: KitOptions) {
        player.inventory.setItem(0, ItemStack(Items.IRON_SWORD).unbreakable())
        player.inventory.setItem(1, ItemStack(Items.BOW).unbreakable())
    }

    override fun unequip(player: ServerPlayer, options: KitOptions) {
        player.inventory.setItem(0, ItemStack.EMPTY)
        player.inventory.setItem(1, ItemStack.EMPTY)
    }
}
