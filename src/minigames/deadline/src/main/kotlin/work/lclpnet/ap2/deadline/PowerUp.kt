package work.lclpnet.ap2.deadline

import net.minecraft.core.RegistryAccess
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import work.lclpnet.ap2.impl.game.item.SpecialItem
import work.lclpnet.ap2.impl.game.item.SpecialItemContext

/**
 * An activatable power-up that riders collect from pickups and trigger by using its item.
 */
abstract class PowerUp(protected val riders: Riders) : SpecialItem {

    protected abstract val item: Item

    /** How long the effect lasts in ticks, conveyed to the rider as an item cooldown. Zero for instant effects. */
    protected abstract val duration: Int

    /** The sound played to the rider when the power-up activates. */
    protected abstract val sound: GameSound

    protected abstract fun activate(rider: ServerPlayer, cycle: LightCycle, ctx: SpecialItemContext)

    override fun createItemStack(registryManager: RegistryAccess): ItemStack = ItemStack(item)

    override fun canBeDropped(player: ServerPlayer, stack: ItemStack): Boolean = false

    override fun onUse(player: ServerPlayer, stack: ItemStack, hand: InteractionHand?, ctx: SpecialItemContext): InteractionResult {
        val cycle = riders.cycle(player.uuid) ?: return InteractionResult.PASS

        if (duration > 0) {
            // the item stays in the slot with its cooldown sweep while the effect lasts, then disappears
            player.cooldowns.addCooldown(stack, duration)
            ctx.scheduler().timeout(duration) { -> ctx.removeSpecialItem(player, this) }
        } else {
            ctx.removeSpecialItem(player, this)
        }

        activate(player, cycle, ctx)
        sound.playTo(player)

        return InteractionResult.SUCCESS_SERVER
    }
}
