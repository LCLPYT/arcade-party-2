package work.lclpnet.ap2.game

import net.minecraft.server.level.ServerLevel
import work.lclpnet.ap2.game.player.ParticipantListener
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

interface MiniGameInstance {

    val gameHandle: MiniGameHandle

    val level: ServerLevel

    val participantListener: ParticipantListener?

    val maxDuration: Duration
        get() = 15.minutes

    fun start()
}