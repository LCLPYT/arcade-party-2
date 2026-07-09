package work.lclpnet.ap2.game.kit.shared

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items.IRON_AXE
import work.lclpnet.ap2.ext.inWholeTicks
import work.lclpnet.ap2.game.kit.KitHandle
import work.lclpnet.ap2.game.kit.KitOptions
import work.lclpnet.ap2.game.kit.SingleItemKit
import work.lclpnet.ap2.game.kit.setupOnUse
import work.lclpnet.kibu.access.VelocityModifier
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class LeapKit(
    handle: KitHandle,
    val leapStrength: Double = 1.8,
    val cooldown: Duration = 3.seconds,
    uses: Int = 3,
    item: Item = IRON_AXE,
) : SingleItemKit(handle, ID, item, uses) {

    override fun init(options: KitOptions) {
        setupOnUse(::useItem)
    }

    private fun useItem(player: ServerPlayer, stack: ItemStack) {
        stack.consume(1, player)
        player.cooldowns.addCooldown(stack, cooldown.inWholeTicks.toInt())

        VelocityModifier.setVelocity(player, player.lookAngle.scale(leapStrength))

        val world = player.level()

        world.playSound(null, player.x, player.y, player.z,
            SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.5f, 1.8f)

        world.sendParticles(
            ParticleTypes.CLOUD,
            player.x,
            player.y,
            player.z,
            25,
            0.2,
            0.5,
            0.2,
            0.2
        )
    }

    companion object {
        const val ID = "leap"
    }
}