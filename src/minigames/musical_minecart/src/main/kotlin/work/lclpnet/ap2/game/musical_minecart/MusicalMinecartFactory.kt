package work.lclpnet.ap2.game.musical_minecart

import kotlinx.coroutines.future.await
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.util.openRandomMap
import work.lclpnet.ap2.impl.music.SongHandler
import java.util.*

class MusicalMinecartFactory : MiniGameFactory {
    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val (level, map) = handle.openRandomMap()

        val random = Random()
        val songs = SongHandler(handle, random)
        songs.loadSongs(handle.gameInfo.id).await()

        return MusicalMinecartInstance(handle, level, map, random, songs)
    }
}
