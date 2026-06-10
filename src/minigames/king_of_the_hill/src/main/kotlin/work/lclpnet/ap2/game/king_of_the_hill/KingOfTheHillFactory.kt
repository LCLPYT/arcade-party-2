package work.lclpnet.ap2.game.king_of_the_hill

import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.util.openRandomMap

class KingOfTheHillFactory : MiniGameFactory {
    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val (level, map) = handle.openRandomMap()

        createMarkers(level, map)

        return KingOfTheHillInstance(handle, level, map)
    }
}
