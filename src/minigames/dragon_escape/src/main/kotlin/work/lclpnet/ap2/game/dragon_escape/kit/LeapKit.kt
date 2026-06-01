package work.lclpnet.ap2.game.dragon_escape.kit

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.ext.mc.isOf
import work.lclpnet.ap2.game.kit.KitHandle
import work.lclpnet.ap2.game.kit.KitOptions
import work.lclpnet.ap2.game.kit.SingleItemKit
import work.lclpnet.kibu.access.VelocityModifier
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks
import work.lclpnet.kibu.scheduler.Ticks

private const val ID = "leap"
private val ITEM: Item = Items.IRON_AXE
private const val USES = 3
private val COOLDOWN_TICKS = Ticks.seconds(3)
private const val LEAP_STRENGTH = 1.8

class LeapKit(handle: KitHandle) : SingleItemKit(handle, ID, ITEM, USES) {

    override fun init(options: KitOptions) {
        PlayerInteractionHooks.USE_ITEM.registerWith(handle.hooks) { player, _, hand ->
            if (player !is ServerPlayer) return@registerWith InteractionResult.PASS

            val stack = player.getItemInHand(hand)

            if (stack.isOf(ITEM) && !player.cooldowns.isOnCooldown(stack)) {
                useItem(player, stack)
                return@registerWith InteractionResult.SUCCESS_SERVER
            }

            InteractionResult.PASS
        }
    }

    private fun useItem(player: ServerPlayer, stack: ItemStack) {
        stack.consume(1, player)
        player.cooldowns.addCooldown(stack, COOLDOWN_TICKS)

        VelocityModifier.setVelocity(player, player.lookAngle.scale(LEAP_STRENGTH))

        val world = player.level()

        world.playSound(null, player.x, player.y, player.z,
            SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.5f, 1.8f)

        world.sendParticles(ParticleTypes.CLOUD, player.x, player.y, player.z, 25,
            0.2, 0.5, 0.2, 0.2)
    }
}
