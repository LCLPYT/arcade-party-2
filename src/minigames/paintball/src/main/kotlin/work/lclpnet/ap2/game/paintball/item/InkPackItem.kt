package work.lclpnet.ap2.game.paintball.item

import net.minecraft.core.RegistryAccess
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.game.item.SpecialItem
import work.lclpnet.ap2.game.item.SpecialItemContext
import work.lclpnet.ap2.game.paintball.util.PaintGunManager

class InkPackItem(
    private val paintGunManager: PaintGunManager,
    private val onUsed: (ServerPlayer) -> Unit
) : SpecialItem {

    override val id = "ink_pack"

    override fun createItemStack(registryManager: RegistryAccess): ItemStack = ItemStack(Items.INK_SAC)

    override fun shouldTransferToInventory(player: ServerPlayer) = false

    override fun onPickedUp(player: ServerPlayer, stack: ItemStack, ctx: SpecialItemContext) {
        paintGunManager.refillPaintGun(player)
        onUsed(player)
    }
}
