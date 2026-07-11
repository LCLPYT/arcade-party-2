package work.lclpnet.ap2.deadline.item

import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import work.lclpnet.ap2.deadline.rider.LightCycle
import work.lclpnet.ap2.deadline.rider.Riders
import work.lclpnet.ap2.deadline.util.GameSound
import work.lclpnet.ap2.impl.game.item.SpecialItemContext

private const val JUMP_STRENGTH = 0.7 // upward velocity in blocks per tick

/**
 * Launches the sheep into the air, so the rider can hop over a trail.
 */
class Jump(riders: Riders, onUsed: (ServerPlayer) -> Unit) : PowerUp(riders, onUsed) {

    override val item: Item = Items.RABBIT_FOOT
    override val duration = 0 // the leap is instant
    override val sound = GameSound(SoundEvents.HORSE_JUMP, 0.8f, 1f)

    override fun id() = "jump"

    override fun activate(rider: ServerPlayer, cycle: LightCycle, ctx: SpecialItemContext) {
        cycle.jump(JUMP_STRENGTH)
    }
}
