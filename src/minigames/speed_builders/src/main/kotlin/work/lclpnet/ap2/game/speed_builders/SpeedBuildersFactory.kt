package work.lclpnet.ap2.game.speed_builders

import kotlinx.coroutines.future.await
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.speed_builders.util.SbSetup
import work.lclpnet.ap2.game.util.openRandomMap
import java.util.*

class SpeedBuildersFactory : MiniGameFactory {

    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val (level, map) = handle.openRandomMap()

        val random = Random()
        val setup = SbSetup(random, handle.logger)

        setup.setup(map, level).await()

        val islands = setup.createIslands(handle.participants, level)

        return SpeedBuildersInstance(handle, level, map, setup, islands)
    }
}