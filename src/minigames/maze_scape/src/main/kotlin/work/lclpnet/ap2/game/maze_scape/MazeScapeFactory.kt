package work.lclpnet.ap2.game.maze_scape

import kotlinx.coroutines.future.await
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.util.model.ModelManager
import work.lclpnet.ap2.ext.mc.setDayTime
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.maze_scape.setup.MSDebugController
import work.lclpnet.ap2.game.maze_scape.setup.MSGenerator
import work.lclpnet.ap2.game.maze_scape.setup.MSLoader
import work.lclpnet.ap2.game.util.openRandomMap
import work.lclpnet.ap2.game.util.useDebugController
import work.lclpnet.ap2.impl.resource.ApResources
import java.util.*

class MazeScapeFactory : MiniGameFactory {

    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val (level, map) = handle.openRandomMap()

        level.setDayTime(18000)

        val modelManager: ModelManager = ApResources.getInstance()

        val parentDebugController = useDebugController(level)
        val debugController = MSDebugController(parentDebugController)

        if (ApConstants.DEBUG) {
            debugController.init(modelManager)
        }

        val setup = MSLoader(level, map, handle.logger)

        val res = setup.load().await()

        val struct = if (MSLoader.DEBUG_PIECES) {
            null
        } else {
            val seed = Random().nextLong()
            val random = Random(seed)

            val generator = MSGenerator(level, map, res, random, seed, handle.logger, debugController)

            generator.startGenerator().await().orElse(null)
        }

        return MazeScapeInstance(handle, level, map, struct, debugController)
    }
}