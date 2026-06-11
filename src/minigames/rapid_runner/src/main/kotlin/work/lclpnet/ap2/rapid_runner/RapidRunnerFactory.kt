package work.lclpnet.ap2.rapid_runner

import kotlinx.coroutines.future.await
import net.minecraft.world.level.dimension.BuiltinDimensionTypes
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.util.GameLevels
import work.lclpnet.ap2.game.util.overworldGenerator
import work.lclpnet.kibu.hook.util.PositionRotation
import xyz.nucleoid.fantasy.Fantasy
import xyz.nucleoid.fantasy.RuntimeLevelConfig
import java.util.concurrent.CompletableFuture
import kotlin.random.Random

class RapidRunnerFactory : MiniGameFactory {

    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val level = handle.worldFacade.changeLevel(
            handle.gameInfo.identifier("overworld"),
            GameLevels.TemporaryNoTeleport,
            { _ ->
                CompletableFuture.completedFuture(PositionRotation(0.0, 0.0, 0.0, 0f, 0f))
            },
            { key ->
                val generator = overworldGenerator(handle.server)

                val config = RuntimeLevelConfig()
                    .setSeed(Random.nextLong())
                    .setDimensionType(BuiltinDimensionTypes.OVERWORLD)
                    .setGenerator(generator)
                    .setShouldTickTime(true)

                val levelHandle = Fantasy.get(handle.server)
                    .openTemporaryLevel(key.identifier(), config)

                CompletableFuture.completedFuture(levelHandle)
            }
        ).await()

        return RapidRunnerInstance(handle, level)
    }
}