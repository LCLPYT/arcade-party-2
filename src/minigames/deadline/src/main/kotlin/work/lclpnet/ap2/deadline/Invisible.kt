package work.lclpnet.ap2.deadline

import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import work.lclpnet.kibu.scheduler.Ticks
import java.util.UUID

private val ACTIVATE_SOUND = GameSound(SoundEvents.BAT_TAKEOFF, 0.8f, 1f)

/**
 * Turns the rider and their sheep invisible for a short duration, during which they also phase through trails.
 */
class Invisible(override val cycles: (UUID) -> LightCycle?) : PowerUp {

    override val item: Item = Items.FERMENTED_SPIDER_EYE
    override val duration = Ticks.seconds(4)

    override fun id() = "invisible"

    override fun activate(rider: ServerPlayer, cycle: LightCycle) {
        cycle.phase(duration)
        rider.addEffect(MobEffectInstance(MobEffects.INVISIBILITY, duration, 0, false, false, false))
        cycle.sheep.addEffect(MobEffectInstance(MobEffects.INVISIBILITY, duration, 0, false, false, false))
        ACTIVATE_SOUND.playTo(rider)
    }
}
