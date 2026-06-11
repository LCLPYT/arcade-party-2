package work.lclpnet.ap2.game.util

import kotlinx.coroutines.future.await
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.MultiNoiseBiomeSource
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists
import net.minecraft.world.level.dimension.BuiltinDimensionTypes
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.game.api.WorldOptions
import work.lclpnet.kibu.hook.util.PositionRotation
import xyz.nucleoid.fantasy.Fantasy
import xyz.nucleoid.fantasy.RuntimeLevelConfig
import xyz.nucleoid.fantasy.RuntimeLevelHandle
import java.util.concurrent.CompletableFuture
import kotlin.random.Random

object GameLevels {
    val TemporaryNoTeleport = object : WorldOptions {
        override fun shouldBeDeleted() = true
        override fun isCleanMapRequired() = true
        override fun shouldTeleportPlayers() = false
    }
}

suspend fun MiniGameHandle.generateRandomLevel(
    factory: (ResourceKey<Level>) -> CompletableFuture<RuntimeLevelHandle> = ::createOverworldLevel
): ServerLevel {
    return worldFacade.changeLevel(
        gameInfo.identifier("overworld"),
        GameLevels.TemporaryNoTeleport,
        { _ ->
            CompletableFuture.completedFuture(PositionRotation(0.0, 0.0, 0.0, 0f, 0f))
        },
       factory
    ).await()
}

fun MiniGameHandle.createOverworldLevel(
    key: ResourceKey<Level>,
): CompletableFuture<RuntimeLevelHandle> {
    val generator = overworldGenerator(server)

    val config = RuntimeLevelConfig()
        .setSeed(Random.nextLong())
        .setDimensionType(BuiltinDimensionTypes.OVERWORLD)
        .setGenerator(generator)
        .setShouldTickTime(true)

    val levelHandle = Fantasy.get(server)
        .openTemporaryLevel(key.identifier(), config)

    return CompletableFuture.completedFuture(levelHandle)
}

fun overworldGenerator(server: MinecraftServer): NoiseBasedChunkGenerator {
    val registryAccess = server.registryAccess()

    val noiseSettings = registryAccess.lookupOrThrow(Registries.NOISE_SETTINGS)
    val overworldNoiseSettings = noiseSettings.getOrThrow(NoiseGeneratorSettings.OVERWORLD)

    val paramList = registryAccess.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
    val preset = paramList.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD)
    val biomeSource = MultiNoiseBiomeSource.createFromPreset(preset)

    return NoiseBasedChunkGenerator(biomeSource, overworldNoiseSettings)
}
