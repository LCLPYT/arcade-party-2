package work.lclpnet.ap2.deadline.item

import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import work.lclpnet.ap2.deadline.rider.LightCycle
import work.lclpnet.ap2.deadline.rider.Riders
import work.lclpnet.ap2.impl.game.item.SpecialItemContext
import work.lclpnet.ap2.util.sound.GameSound
import work.lclpnet.kibu.scheduler.Ticks

private const val BOOST_MULTIPLIER = 2f // multiplies the engine force while the boost is active

/**
 * Temporarily gives the sheep a stronger engine at full throttle.
 */
class SpeedBoostPowerUp(riders: Riders, onUsed: (ServerPlayer) -> Unit) : DeadlinePowerUp(riders, onUsed) {

    override val item: Item = Items.SUGAR
    override val duration = Ticks.seconds(4)
    override val sound = GameSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.8f, 1f)

    override fun id() = "speed_boost"

    override fun activate(rider: ServerPlayer, cycle: LightCycle, ctx: SpecialItemContext) {
        cycle.overrideEngine(cycle.spec.engineForce * BOOST_MULTIPLIER, duration)
    }
}
