package work.lclpnet.ap2.deadline

import net.minecraft.world.entity.animal.sheep.Sheep
import net.minecraft.world.entity.player.Input
import work.lclpnet.ap2.game.vehicle.BikeSpec
import work.lclpnet.ap2.game.vehicle.Motorbike

/**
 * A rider's dyed sheep driven like a motorbike, which can temporarily phase through trails.
 */
class LightCycle(val sheep: Sheep, spec: BikeSpec) : Motorbike(sheep, spec) {

    /** Whether the bike currently passes through trails. */
    val phased: Boolean
        get() = phaseTicks > 0

    private var phaseTicks = 0

    fun phase(durationTicks: Int) {
        phaseTicks = durationTicks
    }

    override fun tick(input: Input) {
        if (phaseTicks > 0) phaseTicks--
        super.tick(input)
    }
}
