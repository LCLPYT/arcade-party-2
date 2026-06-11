package work.lclpnet.ap2.rapid_runner

import kotlinx.coroutines.future.await
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.biome.MultiNoiseBiomeSource
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists
import net.minecraft.world.level.dimension.BuiltinDimensionTypes
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.game.api.WorldOptions
import work.lclpnet.kibu.hook.util.PositionRotation
import xyz.nucleoid.fantasy.Fantasy
import xyz.nucleoid.fantasy.RuntimeLevelConfig
import java.util.concurrent.CompletableFuture
import kotlin.random.Random

class RapidRunnerFactory : MiniGameFactory {

    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val level = handle.worldFacade.changeLevel(
            handle.gameInfo.identifier("overworld"),
            WorldOptions.TEMPORARY,
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

    private fun overworldGenerator(server: MinecraftServer): NoiseBasedChunkGenerator {
        val registryAccess = server.registryAccess()

        val noiseSettings = registryAccess.lookupOrThrow(Registries.NOISE_SETTINGS)
        val overworldNoiseSettings = noiseSettings.getOrThrow(NoiseGeneratorSettings.OVERWORLD)

        val paramList = registryAccess.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
        val preset = paramList.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD)
        val biomeSource = MultiNoiseBiomeSource.createFromPreset(preset)

        return NoiseBasedChunkGenerator(biomeSource, overworldNoiseSettings)
    }
}