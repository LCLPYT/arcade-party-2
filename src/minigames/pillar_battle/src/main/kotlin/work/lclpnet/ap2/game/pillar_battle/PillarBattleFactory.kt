package work.lclpnet.ap2.game.pillar_battle

import kotlinx.coroutines.future.await
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.openRandomMap
import java.util.*

class PillarBattleFactory : MiniGameFactory {
    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val (level, map) = openRandomMap(handle)

        val random = Random()

        val setup = PbSetup(level, map, handle.logger)
        setup.load().await()

        val pillars = setup.placePillars(handle.participants, random)

        return PillarBattleInstance(handle, level, map, random, pillars)
    }
}
