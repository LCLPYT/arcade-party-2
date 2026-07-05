package work.lclpnet.ap2.mode_default.util

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks
import work.lclpnet.kibu.inv.prompt.OptionPrompt
import work.lclpnet.kibu.inv.type.RestrictedInventory

/**
 * A simple inventory menu of clickable buttons. Each occupied slot maps to a click action.
 *
 * Navigation between screens is done by opening another menu from within an action
 * ([ServerPlayer.openMenu] / [open]); there are no futures and no per-menu hook registration, since
 * clicks on inventories implementing [OptionPrompt.Handler] are dispatched globally by the kibu
 * inventory module.
 */
class ClickableMenu(rows: Int, title: Component) : RestrictedInventory(rows, title), OptionPrompt.Handler {

    private val actions = HashMap<Int, (ServerPlayer) -> Unit>()

    fun button(slot: Int, icon: ItemStack, onClick: (ServerPlayer) -> Unit) {
        setItem(slot, icon)
        actions[slot] = onClick
    }

    override fun onClick(event: PlayerInventoryHooks.ClickEvent) {
        val slot = event.handlerSlot() ?: return
        actions[slot.containerSlot]?.invoke(event.player())
    }
}
