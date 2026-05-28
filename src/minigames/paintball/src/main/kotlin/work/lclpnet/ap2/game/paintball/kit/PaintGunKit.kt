package work.lclpnet.ap2.game.paintball.kit

import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.UseCooldown
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.game.paintball.util.PaintGun
import work.lclpnet.ap2.game.paintball.util.PaintGunManager
import work.lclpnet.ap2.impl.game.kit.KitHandle
import work.lclpnet.ap2.impl.game.kit.KitOptions
import work.lclpnet.ap2.impl.game.kit.SingleItemKit
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import java.util.*

open class PaintGunKit(
    handle: KitHandle,
    id: String,
    item: Item,
    count: Int,
    val paintGun: PaintGun,
    private val paintGunManager: PaintGunManager
) : SingleItemKit(handle, id, item, count) {

    override fun init(options: KitOptions) {
        PlayerInteractionHooks.USE_ITEM.registerWith(handle.hooks()) { player, _, hand ->
            if (player !is ServerPlayer) return@registerWith InteractionResult.PASS

            val stack = player.getItemInHand(hand)

            if (!stack.isOf(item)) return@registerWith InteractionResult.PASS

            paintGunManager.shoot(player, paintGun, stack)

            InteractionResult.SUCCESS
        }
    }

    override fun configureItemStack(stack: ItemStack) {
        super.configureItemStack(stack)

        val group = Optional.of(ApConstants.identifier(paintGun.id))
        stack.set(DataComponents.USE_COOLDOWN, UseCooldown(paintGun.cooldownTicks.toFloat(), group))
        stack.set(DataComponents.MAX_DAMAGE, paintGun.ammo)
    }
}
