package work.lclpnet.ap2.game.jump_and_run

import kotlinx.coroutines.future.await
import work.lclpnet.ap2.ext.mc.setDayTime
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.jump_and_run.gen.JumpAndRunSetup
import work.lclpnet.ap2.game.util.openRandomMap

class JumpAndRunFactory : MiniGameFactory {

    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val (level, map) = handle.openRandomMap()

        level.setDayTime(4000)

        val setup = JumpAndRunSetup(handle, map, level, JumpAndRunInstance.TARGET_MINUTES)
        val jumpAndRun = setup.setup().await()

        return JumpAndRunInstance(handle, level, map, jumpAndRun)
    }
}