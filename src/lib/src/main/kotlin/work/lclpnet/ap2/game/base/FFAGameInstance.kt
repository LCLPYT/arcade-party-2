package work.lclpnet.ap2.game.base

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.api.game.WinManagerAccess
import work.lclpnet.ap2.api.game.WinManagerView
import work.lclpnet.ap2.api.game.data.DataContainer
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.ap2.game.player.ParticipantListener
import work.lclpnet.ap2.game.util.useFFAWinManager
import work.lclpnet.ap2.impl.game.WinManagerAccessImpl
import work.lclpnet.game.map.GameMap
import java.util.*

abstract class FFAGameInstance(
    gameHandle: MiniGameHandle,
    world: ServerLevel,
    map: GameMap
) : MapGameInstance(gameHandle, world, map), ParticipantListener, WinManagerView {

    @JvmField
    protected val winManager = useFFAWinManager(map) { data }

    override val participantListener = this

    override fun participantRemoved(player: ServerPlayer) {
        // this will be called when a participant quits or is eliminated
        winManager.checkForLastRemaining()
    }

    override fun getWinManagerAccess(): WinManagerAccess {
        return WinManagerAccessImpl(
            winManager,
            { value -> Optional.of(value) },
            data
        )
    }

    protected abstract val data: DataContainer<ServerPlayer, PlayerRef>
}