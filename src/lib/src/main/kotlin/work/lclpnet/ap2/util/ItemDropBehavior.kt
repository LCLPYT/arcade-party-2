package work.lclpnet.ap2.util

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks

fun disallowDropItem(hooks: HookRegistrar, itemMatcher: (ItemStack) -> Boolean) {
    PlayerInventoryHooks.DROP_ITEM.registerWith(hooks) { player, slot, inInventory ->
        val stack = when {
            !inInventory -> player.inventory.getItem(slot)
            slot in player.containerMenu.slots.indices -> player.containerMenu.getSlot(slot).item
            slot == -999 -> player.containerMenu.carried
            else -> ItemStack.EMPTY
        }

        !itemMatcher(stack)
    }

    ServerLivingEntityHooks.ALLOW_DEATH.registerWith(hooks) { entity, _, _ ->
        if (entity is ServerPlayer) {
            for (i in 0 until entity.inventory.containerSize) {
                val stack = entity.inventory.getItem(i)

                if (itemMatcher(stack)) {
                    entity.inventory.removeItemNoUpdate(i)
                }
            }
        }

        true
    }
}