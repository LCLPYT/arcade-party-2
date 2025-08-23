package work.lclpnet.ap2.game.dance_floor

import net.minecraft.server.world.ServerWorld
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.map.MapBootstrap
import work.lclpnet.ap2.api.music.ConfiguredSong
import work.lclpnet.ap2.api.music.SongWrapper
import work.lclpnet.ap2.game.dance_floor.cmd.SetSongCommand
import work.lclpnet.ap2.game.dance_floor.cmd.SkipSongCommand
import work.lclpnet.ap2.impl.game.EliminationGameInstance
import work.lclpnet.ap2.impl.music.SongHandler
import work.lclpnet.ap2.impl.util.Hints
import work.lclpnet.lobby.game.map.GameMap
import java.util.*
import java.util.concurrent.CompletableFuture

class DanceFloorInstance(gameHandle: MiniGameHandle) : EliminationGameInstance(gameHandle), MapBootstrap {

    val random = Random()
    val songHandler = SongHandler(gameHandle, random)
    var loadingSong: CompletableFuture<ConfiguredSong>? = null
    var songWrapper: SongWrapper? = null

    override fun createWorldBootstrap(world: ServerWorld, map: GameMap): CompletableFuture<Void> {
        return songHandler.loadSongs(gameHandle.gameInfo.id)
    }

    override fun prepare() {
        SetSongCommand(songHandler, this::nextSong).register(gameHandle.commandRegistrar)
        SkipSongCommand(this::nextSong).register(gameHandle.commandRegistrar)

        Hints(gameHandle).sendBeforeReady(this, Hints.Mod.NOTICA)
    }

    override fun ready() {
        nextSong()
    }

    @Synchronized
    private fun nextSong() {
        if (loadingSong != null) return

        loadingSong = songHandler.getNextSong()
        songWrapper?.stop()

        loadingSong!!.thenAccept { song ->
            songWrapper = songHandler.play(song, gameHandle.server)
            loadingSong = null
        }
    }
}