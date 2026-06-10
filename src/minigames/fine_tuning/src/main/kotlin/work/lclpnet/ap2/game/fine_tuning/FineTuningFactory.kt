package work.lclpnet.ap2.game.fine_tuning

import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.util.openRandomMap

class FineTuningFactory : MiniGameFactory {
    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val (level, map) = handle.openRandomMap()

        val setup = FineTuningSetup(handle, map, level)
        setup.createRooms()

        return FineTuningInstance(handle, level, map, setup)
    }
}
