package work.lclpnet.ap2.game.fine_tuning

import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.openRandomMap

class FineTuningFactory : MiniGameFactory {
    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val (level, map) = openRandomMap(handle)

        val setup = FineTuningSetup(handle, map, level)
        setup.createRooms()

        return FineTuningInstance(handle, level, map, setup)
    }
}
