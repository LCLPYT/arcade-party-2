package work.lclpnet.ap2.rapid_runner

import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.player.ParticipantListener
import work.lclpnet.game.util.ProtectorUtils

class RapidRunnerInstance(val gameHandle: MiniGameHandle) : MiniGameInstance {

    override fun start() {
        gameHandle.protect { config ->
            config.disallowAll()

            ProtectorUtils.allowCreativeOperatorBypass(config)
        }
    }

    override val participantListener: ParticipantListener?
        get() = null
}