package work.lclpnet.ap2.game.dance_floor

import kotlinx.coroutines.future.await
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.openRandomMap
import work.lclpnet.ap2.impl.music.SongHandler
import kotlin.random.Random
import kotlin.random.asJavaRandom

class DanceFloorFactory : MiniGameFactory {
    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val (level, map) = openRandomMap(handle)

        val songHandler = SongHandler(handle, Random.asJavaRandom())
        songHandler.loadSongs(handle.gameInfo.id).await()

        return DanceFloorInstance(handle, level, map, songHandler)
    }
}
