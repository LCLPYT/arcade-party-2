package work.lclpnet.ap2.deadline

import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import work.lclpnet.ap2.impl.game.item.SpecialItemContext
import work.lclpnet.kibu.scheduler.Ticks
import java.util.UUID

/**
 * Turns the rider and their sheep invisible for a short duration, during which they also phase through trails.
 */
class Invisible(cycles: (UUID) -> LightCycle?) : PowerUp(cycles) {

    override val item: Item = Items.FERMENTED_SPIDER_EYE
    override val duration = Ticks.seconds(4)
    override val sound = GameSound(SoundEvents.BAT_TAKEOFF, 0.8f, 1f)

    override fun id() = "invisible"

    override fun activate(rider: ServerPlayer, cycle: LightCycle, ctx: SpecialItemContext) {
        cycle.phase(duration)
        rider.addEffect(MobEffectInstance(MobEffects.INVISIBILITY, duration, 0, false, false, false))
        cycle.sheep.addEffect(MobEffectInstance(MobEffects.INVISIBILITY, duration, 0, false, false, false))

        // armor stays visible despite the invisibility effect, so temporarily unequip it
        RiderOutfit.unequip(rider)

        ctx.scheduler().timeout(duration) { ->
            // eliminated riders already had their inventory reset
            val current = cycles(rider.uuid) ?: return@timeout

            RiderOutfit.equip(rider, current.sheep.color)
        }
    }
}
