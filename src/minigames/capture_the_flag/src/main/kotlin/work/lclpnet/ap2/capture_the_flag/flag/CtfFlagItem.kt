package work.lclpnet.ap2.capture_the_flag.flag

import net.minecraft.core.RegistryAccess
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import work.lclpnet.ap2.game.item.SpecialItem

/**
 * Marker item for dropped flags.
 * The flag state is tracked by the flag manager.
 */
class CtfFlagItem(private val flag: Flag) : SpecialItem {

    override val id = "flag"

    override fun createItemStack(registryManager: RegistryAccess): ItemStack = flag.carryStack.copy()

    override fun shouldTransferToInventory(player: ServerPlayer) = false
}
