package work.lclpnet.ap2.game.base

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.data.DataContainer
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.ap2.game.player.ParticipantListener
import work.lclpnet.ap2.game.util.useFFAWinManager
import work.lclpnet.game.map.GameMap

abstract class FFAGameInstance(
    gameHandle: MiniGameHandle,
    world: ServerLevel,
    map: GameMap
) : MapGameInstance(gameHandle, world, map), ParticipantListener {

    override val winManager = useFFAWinManager(map) { data }

    override val participantListener = this

    override fun participantRemoved(player: ServerPlayer) {
        // this will be called when a participant quits or is eliminated
        winManager.checkForLastRemaining()
    }

    protected abstract val data: DataContainer<ServerPlayer, PlayerRef>
}