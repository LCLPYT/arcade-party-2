package work.lclpnet.ap2.deadline.util

import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import work.lclpnet.ap2.ext.mc.playNotifySound

/**
 * A sound effect with a fixed volume and pitch that can be played to individual players.
 */
class GameSound(private val event: SoundEvent, private val volume: Float, private val pitch: Float) {

    fun playTo(player: ServerPlayer) {
        player.playNotifySound(event, SoundSource.PLAYERS, volume, pitch)
    }
}
