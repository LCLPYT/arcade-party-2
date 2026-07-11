package work.lclpnet.ap2.deadline.vehicle

import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket
import net.minecraft.server.level.ServerPlayer
import kotlin.math.roundToInt

/**
 * Repurposes the xp bar as the rider's speedometer.
 */
object SpeedHud {

    // show the rider's speed on the xp bar in km/h
    fun show(rider: ServerPlayer, bike: Motorbike) {
        val kmh = (bike.speed * 3.6f).roundToInt()
        rider.connection.send(ClientboundSetExperiencePacket(bike.speedFraction, 0, kmh))
    }

    // reset the xp bar readout when a rider stops riding
    fun clear(rider: ServerPlayer) {
        rider.connection.send(ClientboundSetExperiencePacket(0f, 0, 0))
    }
}
