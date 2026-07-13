package work.lclpnet.ap2.game.util

import kotlinx.coroutines.future.await
import net.minecraft.core.BlockPos
import net.minecraft.core.GlobalPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.PlayerSpawnFinder
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Mth
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.MultiNoiseBiomeSource
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.dimension.BuiltinDimensionTypes
import net.minecraft.world.level.gamerules.GameRules
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings
import net.minecraft.world.level.storage.LevelData
import work.lclpnet.ap2.ext.mc.setBlock
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.gaco.ds.BlockBox
import work.lclpnet.game.api.WorldOptions
import work.lclpnet.game.util.ResetWorldModifier
import work.lclpnet.kibu.hook.util.PositionRotation
import xyz.nucleoid.fantasy.Fantasy
import xyz.nucleoid.fantasy.RuntimeLevelConfig
import xyz.nucleoid.fantasy.RuntimeLevelHandle
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier
import kotlin.random.Random

object GameLevels {
    val TemporaryNoTeleport = object : WorldOptions {
        override fun shouldBeDeleted() = true
        override fun isCleanMapRequired() = true
        override fun shouldTeleportPlayers() = false
    }
}

fun setupGameLevel(level: ServerLevel) {
    val server = level.server

    level.gameRules.apply {
        set(GameRules.IMMEDIATE_RESPAWN, true, server)
        set(GameRules.SHOW_ADVANCEMENT_MESSAGES, false, server)
        set(GameRules.PVP, true, server)
    }
}

suspend fun MiniGameHandle.generateRandomLevel(
    factory: (ResourceKey<Level>) -> CompletableFuture<RuntimeLevelHandle> = ::createOverworldLevel
): ServerLevel {
    val level = worldFacade.changeLevel(
        gameInfo.identifier("overworld"),
        GameLevels.TemporaryNoTeleport,
        { level ->
            server.submit(Supplier {
                val spawn = findInitialSpawn(level)

                level.respawnData = LevelData.RespawnData(GlobalPos(level.dimension(), spawn), 0f, 0f)

                PositionRotation(spawn.x + 0.5, spawn.y + 0.5, spawn.z + 0.5, 0f, 0f)
            })
        },
       factory
    ).await()

    setupGameLevel(level)

    return level
}

/**
 * Determines the initial spawn position of a freshly generated level.
 * Mirrors the vanilla logic in {@code MinecraftServer.setInitialSpawn}: first the spawn chunk
 * is located via the climate sampler, then a valid block position is searched in a spiral of
 * chunks around it. This must run on the server thread, since it triggers chunk generation.
 */
fun findInitialSpawn(level: ServerLevel): BlockPos {
    val chunkSource = level.chunkSource
    val spawnChunk = ChunkPos.containing(chunkSource.randomState().sampler().findSpawnPosition())

    var height = chunkSource.generator.getSpawnHeight(level)

    if (height < level.minY) {
        val worldPosition = spawnChunk.worldPosition
        height = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldPosition.x + 8, worldPosition.z + 8)
    }

    var spawnPos = spawnChunk.worldPosition.offset(8, height, 8)

    var xChunkOffset = 0
    var zChunkOffset = 0
    var dXChunk = 0
    var dZChunk = -1

    repeat(Mth.square(11)) {
        if (xChunkOffset in -5..5 && zChunkOffset in -5..5) {
            val testedPos = PlayerSpawnFinder.getSpawnPosInChunk(
                level, ChunkPos(spawnChunk.x + xChunkOffset, spawnChunk.z + zChunkOffset)
            )

            if (testedPos != null) {
                spawnPos = testedPos
                return@repeat
            }
        }

        if (xChunkOffset == zChunkOffset
            || (xChunkOffset < 0 && xChunkOffset == -zChunkOffset)
            || (xChunkOffset > 0 && xChunkOffset == 1 - zChunkOffset)) {
            val oldDx = dXChunk
            dXChunk = -dZChunk
            dZChunk = oldDx
        }

        xChunkOffset += dXChunk
        zChunkOffset += dZChunk
    }

    return spawnPos
}

fun MiniGameHandle.createOverworldLevel(
    key: ResourceKey<Level>,
    seed: Long = Random.nextLong()
): CompletableFuture<RuntimeLevelHandle> {
    val generator = overworldGenerator(server)

    val config = RuntimeLevelConfig()
        .setSeed(seed)
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

fun MiniGameHandle.placeBarrierAtSpawnFloor(level: ServerLevel, radius: Int = 4, height: Int = 4): ResetWorldModifier {
    val walls = ResetWorldModifier(level, hooks)

    val spawn = level.respawnData.pos()

    val box = BlockBox(spawn.offset(-radius, -1, -radius), spawn.offset(radius, height, radius))
    val flags = Block.UPDATE_SUPPRESS_DROPS or Block.UPDATE_KNOWN_SHAPE

    for (pos in box) {
        if (box.isBorder(pos)) {
            val state = level.getBlockState(pos)

            if (state.isCollisionShapeFullBlock(level, pos)) continue

            walls.setBlockState(pos, Blocks.BARRIER.defaultBlockState(), flags)
        } else {
            if (!level.getFluidState(pos).isEmpty) {
                walls.setBlockState(pos, Blocks.AIR.defaultBlockState(), flags)
            }
        }
    }

    return walls
}

fun MiniGameHandle.createBarrierPlatformAboveSpawnGround(
    level: ServerLevel,
    radius: Int = 4,
    height: Int = 4,
    groundOffset: Int = 40
): ResetWorldModifier {
    val walls = ResetWorldModifier(level, hooks)

    val pos = findPlatformPos(level, level.respawnData.pos(), height, groundOffset)

    val box = BlockBox(
        pos.offset(-radius, -1, -radius),
        pos.offset(radius, height, radius)
    )

    val flags = Block.UPDATE_SUPPRESS_DROPS or Block.UPDATE_KNOWN_SHAPE

    for (pos in box) {
        if (box.isBorder(pos)) {
            val state = level.getBlockState(pos)

            if (state.isCollisionShapeFullBlock(level, pos)) continue

            walls.setBlockState(pos, Blocks.BARRIER.defaultBlockState(), flags)
        } else {
            level.setBlock(pos, Blocks.AIR)
        }
    }

    level.respawnData = LevelData.RespawnData(
        GlobalPos(level.dimension(), pos),
        0f,
        0f
    )

    return walls
}

private fun findPlatformPos(
    level: ServerLevel,
    spawnPos: BlockPos,
    height: Int,
    groundOffset: Int
): BlockPos {
    val maxY = level.maxY - height
    val highestY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, spawnPos)

    val platformY = (highestY + groundOffset).coerceAtMost(maxY)

    return spawnPos.atY(platformY)
}
