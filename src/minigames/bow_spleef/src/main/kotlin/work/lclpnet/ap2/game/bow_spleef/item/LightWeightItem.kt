package work.lclpnet.ap2.game.bow_spleef.item

import net.minecraft.core.RegistryAccess
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.impl.game.item.SpecialItem
import work.lclpnet.ap2.impl.game.item.SpecialItemContext
import work.lclpnet.kibu.access.entity.EntityUtil.resetAttribute
import work.lclpnet.kibu.access.entity.EntityUtil.setAttribute
import work.lclpnet.kibu.scheduler.Ticks

private val DURATION = Ticks.seconds(5)

class LightWeightItem : SpecialItem {

    override fun id(): String = "light_weight"

    override fun createItemStack(registryManager: RegistryAccess): ItemStack = ItemStack(Items.FEATHER)

    override fun canBeDropped(player: ServerPlayer, stack: ItemStack): Boolean = false

    override fun onPickedUp(player: ServerPlayer, stack: ItemStack, ctx: SpecialItemContext) {
        player.cooldowns.addCooldown(stack, DURATION)
        setAttribute(player, GRAVITY, 0.035)

        ctx.scheduler().timeout(DURATION) { ->
            resetAttribute(player, GRAVITY)
            ctx.removeSpecialItem(player, this)
        }

        player.level().playSound(null, player.x, player.eyeY, player.z, SoundEvents.BREEZE_IDLE_GROUND, SoundSource.PLAYERS, 0.65f, 1.5f)
    }
}
