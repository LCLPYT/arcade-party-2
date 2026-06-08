package work.lclpnet.ap2.game.splashy_dropper

import net.minecraft.world.level.gamerules.GameRules
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.openRandomMap
import work.lclpnet.ap2.game.splashy_dropper.data.SdGenerator
import java.util.*

class SplashyDropperFactory : MiniGameFactory {
    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val (level, map) = openRandomMap(handle)

        level.gameRules.set(GameRules.RANDOM_TICK_SPEED, 0, level.server)
        SdGenerator(level, map, Random()).generate()

        return SplashyDropperInstance(handle, level, map)
    }
}
