package work.lclpnet.ap2.game.bow_spleef.item

import net.minecraft.core.RegistryAccess
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.impl.game.item.SpecialItem
import work.lclpnet.ap2.impl.game.item.SpecialItemContext
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks

private const val USES = 3

class FishingRodItem : SpecialItem {

    override fun id(): String = "fishing_rod"

    override fun createItemStack(registryManager: RegistryAccess): ItemStack {
        val stack = ItemStack(Items.FISHING_ROD)
        stack.set(DataComponents.MAX_DAMAGE, USES * 3)
        return stack
    }

    override fun registerHooks(hooks: HookRegistrar, ctx: SpecialItemContext) {
        PlayerInventoryHooks.SLOT_CHANGE.registerWith(hooks) { player, _ ->
            if (!ctx.hasSpecialItem(player, this)) return@registerWith

            val fishing = player.fishing

            if (fishing == null || fishing.isRemoved || fishing.hookedIn == null) return@registerWith

            player.inventory.getItem(8).hurtAndBreak(3, player, EquipmentSlot.MAINHAND)
        }
    }
}
