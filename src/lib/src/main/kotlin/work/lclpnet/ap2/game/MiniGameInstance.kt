package work.lclpnet.ap2.game

import work.lclpnet.ap2.game.player.ParticipantListener
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

interface MiniGameInstance {

    fun start()

    val participantListener: ParticipantListener?

    val maxDuration: Duration
        get() = 15.minutes
}