package work.lclpnet.ap2.deadline

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Item

/**
 * An activatable power-up that riders collect from pickups and trigger by using its item.
 */
interface PowerUp {
    val item: Item
    val nameKey: String

    /** How long the effect lasts in ticks, conveyed to the rider as an item cooldown. Zero for instant effects. */
    val duration: Int

    fun activate(rider: ServerPlayer, cycle: LightCycle)
}
