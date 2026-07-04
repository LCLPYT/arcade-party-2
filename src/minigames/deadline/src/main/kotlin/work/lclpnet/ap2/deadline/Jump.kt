package work.lclpnet.ap2.deadline

import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import work.lclpnet.ap2.impl.game.item.SpecialItemContext
import java.util.UUID

private const val JUMP_STRENGTH = 0.7 // upward velocity in blocks per tick
private val ACTIVATE_SOUND = GameSound(SoundEvents.HORSE_JUMP, 0.8f, 1f)

/**
 * Launches the sheep into the air, so the rider can hop over a trail.
 */
class Jump(override val cycles: (UUID) -> LightCycle?) : PowerUp {

    override val item: Item = Items.RABBIT_FOOT
    override val duration = 0 // the leap is instant

    override fun id() = "jump"

    override fun activate(rider: ServerPlayer, cycle: LightCycle, ctx: SpecialItemContext) {
        cycle.jump(JUMP_STRENGTH)
        ACTIVATE_SOUND.playTo(rider)
    }
}
