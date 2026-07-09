package work.lclpnet.ap2.game.kit

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks

fun SingleItemKit.setupOnUse(onUse: (ServerPlayer, ItemStack) -> Unit) {
    setupOnUse(
        { stack -> stack.isOf(item) },
        onUse
    )
}

fun BaseKit.setupOnUse(
    isSpecialItem: (ItemStack) -> Boolean,
    onUse: (ServerPlayer, ItemStack) -> Unit,
) {
    PlayerInteractionHooks.USE_ITEM.registerWith(handle.hooks) { player, _, hand ->
        if (player !is ServerPlayer) return@registerWith InteractionResult.PASS

        val stack = player.getItemInHand(hand)

        if (isSpecialItem(stack) && !player.cooldowns.isOnCooldown(stack)) {
            onUse(player, stack)
            return@registerWith InteractionResult.SUCCESS_SERVER
        }

        InteractionResult.PASS
    }
}
