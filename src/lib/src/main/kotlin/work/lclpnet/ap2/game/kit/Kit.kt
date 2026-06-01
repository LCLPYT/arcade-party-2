package work.lclpnet.ap2.game.kit

import net.minecraft.core.RegistryAccess
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

interface Kit {
    fun id(): String

    fun createItemStack(manager: RegistryAccess): ItemStack

    fun init(options: KitOptions) {}

    fun equip(player: ServerPlayer, options: KitOptions) {}

    fun unequip(player: ServerPlayer, options: KitOptions) {}
}
