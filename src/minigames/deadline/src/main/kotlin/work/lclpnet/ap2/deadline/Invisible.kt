package work.lclpnet.ap2.deadline

import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.impl.game.item.SpecialItemContext
import work.lclpnet.kibu.scheduler.Ticks
import java.util.UUID

private val ACTIVATE_SOUND = GameSound(SoundEvents.BAT_TAKEOFF, 0.8f, 1f)

private val ARMOR_SLOTS = listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)

/**
 * Turns the rider and their sheep invisible for a short duration, during which they also phase through trails.
 */
class Invisible(override val cycles: (UUID) -> LightCycle?) : PowerUp {

    override val item: Item = Items.FERMENTED_SPIDER_EYE
    override val duration = Ticks.seconds(4)

    override fun id() = "invisible"

    override fun activate(rider: ServerPlayer, cycle: LightCycle, ctx: SpecialItemContext) {
        cycle.phase(duration)
        rider.addEffect(MobEffectInstance(MobEffects.INVISIBILITY, duration, 0, false, false, false))
        cycle.sheep.addEffect(MobEffectInstance(MobEffects.INVISIBILITY, duration, 0, false, false, false))

        // armor stays visible despite the invisibility effect, so temporarily unequip it
        val storedArmor = ARMOR_SLOTS.associateWith { rider.getItemBySlot(it).copy() }

        for (slot in ARMOR_SLOTS) {
            rider.setItemSlot(slot, ItemStack.EMPTY)
        }

        ctx.scheduler().timeout(duration) { ->
            // eliminated riders already had their inventory reset
            if (cycles(rider.uuid) == null) return@timeout

            for ((slot, stack) in storedArmor) {
                rider.setItemSlot(slot, stack)
            }
        }

        ACTIVATE_SOUND.playTo(rider)
    }
}
