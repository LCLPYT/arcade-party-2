package work.lclpnet.ap2.game.player

import net.minecraft.server.level.ServerPlayer

fun interface ParticipantListener {
    fun participantRemoved(player: ServerPlayer)
}
