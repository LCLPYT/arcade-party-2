package work.lclpnet.ap2.rapid_runner

import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.game.MiniGameInstance
import work.lclpnet.ap2.game.player.ParticipantListener
import work.lclpnet.game.util.ProtectorUtils

class RapidRunnerInstance(val gameHandle: MiniGameHandle) : MiniGameInstance {

    override fun start() {
        gameHandle.protect { config ->
            config.disallowAll()

            ProtectorUtils.allowCreativeOperatorBypass(config)
        }
    }

    override fun getParticipantListener(): ParticipantListener? {
        TODO("Not yet implemented")
    }
}