package work.lclpnet.ap2.deadline

import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import work.lclpnet.ap2.impl.game.item.SpecialItemContext
import work.lclpnet.kibu.scheduler.Ticks
import java.util.UUID

private const val BOOST_FORCE = 6000f // engine force while the boost is active
private val ACTIVATE_SOUND = GameSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.8f, 1f)

/**
 * Temporarily gives the sheep a stronger engine at full throttle.
 */
class SpeedBoost(override val cycles: (UUID) -> LightCycle?) : PowerUp {

    override val item: Item = Items.SUGAR
    override val duration = Ticks.seconds(4)

    override fun id() = "speed_boost"

    override fun activate(rider: ServerPlayer, cycle: LightCycle, ctx: SpecialItemContext) {
        cycle.overrideEngine(BOOST_FORCE, duration)
        ACTIVATE_SOUND.playTo(rider)
    }
}
