package work.lclpnet.ap2.deadline

import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items

private const val JUMP_STRENGTH = 0.7 // upward velocity in blocks per tick
private val ACTIVATE_SOUND = GameSound(SoundEvents.HORSE_JUMP, 0.8f, 1f)

/**
 * Launches the sheep into the air, so the rider can hop over a trail.
 */
class Jump : PowerUp {

    override val item: Item = Items.RABBIT_FOOT
    override val nameKey = "power_up.jump"
    override val duration = 0 // the leap is instant

    override fun activate(rider: ServerPlayer, cycle: LightCycle) {
        cycle.jump(JUMP_STRENGTH)
        ACTIVATE_SOUND.playTo(rider)
    }
}
