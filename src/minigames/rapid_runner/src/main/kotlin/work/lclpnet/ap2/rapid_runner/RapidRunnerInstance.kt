package work.lclpnet.ap2.rapid_runner

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.server.level.ServerLevel
import work.lclpnet.ap2.ext.mc.teleport
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.base.configureDefaults
import work.lclpnet.ap2.game.player.ParticipantListener

class RapidRunnerInstance(
    override val gameHandle: MiniGameHandle,
    override val level: ServerLevel,
) : MiniGameInstance {

    override fun start() {
        configureDefaults()

        for (player in PlayerLookup.all(gameHandle.server)) {
            player.teleport(level.respawnData.globalPos.pos, level)
        }
    }

    override val participantListener: ParticipantListener?
        get() = null
}