package work.lclpnet.ap2.deadline.item

import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import work.lclpnet.ap2.deadline.rider.LightCycle
import work.lclpnet.ap2.deadline.rider.Riders
import work.lclpnet.ap2.game.item.SpecialItemContext
import work.lclpnet.ap2.util.sound.GameSound
import work.lclpnet.kibu.scheduler.Ticks

/**
 * Turns the rider and their sheep invisible for a short duration, during which they also phase through trails.
 */
class InvisiblePowerUp(riders: Riders, onUsed: (ServerPlayer) -> Unit) : DeadlinePowerUp(riders, onUsed) {

    override val item: Item = Items.FERMENTED_SPIDER_EYE
    override val duration = Ticks.seconds(4)
    override val sound = GameSound(SoundEvents.BAT_TAKEOFF, 0.8f, 1f)

    override val id = "invisible"

    override fun activate(rider: ServerPlayer, cycle: LightCycle, ctx: SpecialItemContext) {
        cycle.phase(duration)
        riders.vanish(rider, duration)
    }
}
