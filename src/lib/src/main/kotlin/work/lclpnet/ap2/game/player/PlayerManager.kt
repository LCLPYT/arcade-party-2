package work.lclpnet.ap2.game.player

import net.minecraft.server.level.ServerPlayer

interface PlayerManager : Participants {

    fun offer(player: ServerPlayer): Boolean

    fun startPreparation()

    fun startMiniGame()

    fun enterFinale(finalists: Set<ServerPlayer>)

    fun addPermanentSpectator(player: ServerPlayer)

    fun removePermanentSpectator(player: ServerPlayer)

    fun isPermanentSpectator(player: ServerPlayer): Boolean

    fun bind(listener: ParticipantListener?)

    fun leaveFinale()

    val isFinale: Boolean
}
