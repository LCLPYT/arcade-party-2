package work.lclpnet.ap2.rapid_runner

import work.lclpnet.ap2.ext.mc.setDayTime
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.util.generateRandomLevel
import work.lclpnet.ap2.game.util.placeBarrierAtSpawnFloor

class RapidRunnerFactory : MiniGameFactory {

    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val level = handle.generateRandomLevel()

        level.setDayTime(1000)

        val walls = handle.placeBarrierAtSpawnFloor(level)

        return RapidRunnerInstance(handle, level, walls)
    }
}