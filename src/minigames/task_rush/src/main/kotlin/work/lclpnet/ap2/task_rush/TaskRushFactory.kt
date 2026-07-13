package work.lclpnet.ap2.task_rush

import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.util.generateRandomLevel
import work.lclpnet.ap2.game.util.placeBarrierAtSpawnFloor

class TaskRushFactory : MiniGameFactory {

    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val level = handle.generateRandomLevel()
        val walls = handle.placeBarrierAtSpawnFloor(level)

        return TaskRushInstance(handle, level, walls)
    }
}
