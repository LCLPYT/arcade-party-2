package work.lclpnet.ap2.rapid_runner

import net.minecraft.server.level.ServerLevel
import work.lclpnet.ap2.ext.allPlayers
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.player.ParticipantListener
import work.lclpnet.ap2.game.util.configureDefaults
import work.lclpnet.ap2.game.util.useStartup
import work.lclpnet.game.util.ResetWorldModifier

class RapidRunnerInstance(
    override val gameHandle: MiniGameHandle,
    override val level: ServerLevel,
    val walls: ResetWorldModifier,
) : MiniGameInstance {

    override fun start() {
        configureDefaults()

        for (player in allPlayers()) {
            gameHandle.worldFacade.teleport(player)
        }

        useStartup(::go)
    }

    private fun go() {
        walls.undo()
    }

    override val participantListener: ParticipantListener?
        get() = null
}