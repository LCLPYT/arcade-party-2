package work.lclpnet.ap2.game.bow_spleef.item

import net.minecraft.core.RegistryAccess
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.game.item.SpecialItem
import work.lclpnet.ap2.game.item.SpecialItemContext
import work.lclpnet.kibu.scheduler.Ticks

private val DURATION = Ticks.seconds(3)

class LevitationItem : SpecialItem {

    override val id = "levitation"

    override fun createItemStack(registryManager: RegistryAccess): ItemStack = ItemStack(Items.BREEZE_ROD)

    override fun canBeDropped(player: ServerPlayer, stack: ItemStack): Boolean = !player.cooldowns.isOnCooldown(stack)

    override fun onUse(player: ServerPlayer, stack: ItemStack, hand: InteractionHand?, ctx: SpecialItemContext): InteractionResult {
        player.cooldowns.addCooldown(stack, DURATION)
        player.addEffect(MobEffectInstance(MobEffects.LEVITATION, DURATION, 4))

        ctx.scheduler.timeout(DURATION) { ->
            ctx.removeSpecialItem(player, this)
        }

        player.level().playSound(null, player.x, player.eyeY, player.z, SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, SoundSource.PLAYERS, 0.5f, 2f)

        return InteractionResult.SUCCESS_SERVER
    }
}
