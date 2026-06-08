package work.lclpnet.ap2.rapid_runner

import net.minecraft.server.level.ServerLevel
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.player.ParticipantListener
import work.lclpnet.game.map.GameMap
import work.lclpnet.game.util.ProtectorUtils

class RapidRunnerInstance(val gameHandle: MiniGameHandle, level: ServerLevel, map: GameMap) : MiniGameInstance {

    override fun start() {
        gameHandle.protect { config ->
            config.disallowAll()

            ProtectorUtils.allowCreativeOperatorBypass(config)
        }
    }

    override val participantListener: ParticipantListener?
        get() = null
}