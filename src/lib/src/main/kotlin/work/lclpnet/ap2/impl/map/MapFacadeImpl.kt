package work.lclpnet.ap2.impl.map

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import org.apache.commons.io.FileUtils
import org.slf4j.Logger
import work.lclpnet.ap2.game.util.setupGameLevel
import work.lclpnet.gaco.asset.AssetRepository
import work.lclpnet.game.api.WorldFacade
import work.lclpnet.game.api.WorldOptions
import work.lclpnet.game.map.GameMap
import work.lclpnet.game.map.MapDescriptor
import work.lclpnet.game.map.MapManager
import work.lclpnet.game.map.MapUtils
import work.lclpnet.kibu.hook.util.PositionRotation
import work.lclpnet.kibu.world.KibuLevels
import work.lclpnet.kibu.world.mixin.MinecraftServerAccessor
import xyz.nucleoid.fantasy.RuntimeLevelHandle
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

class MapFacadeImpl(
    private val worldFacade: WorldFacade,
    private val mapRandomizer: MapRandomizer,
    private val mapManager: MapManager,
    override val assetRepository: AssetRepository,
    private val server: MinecraftServer,
    private val logger: Logger,
) : MapFacade {

    override suspend fun changeMap(identifier: Identifier, options: WorldOptions): ServerLevel {
        val map = mapManager.collection().getMap(identifier).orElse(null)
            ?: throw IllegalStateException("Unknown map $identifier")

        return worldFacade.changeLevel(
            identifier,
            options,
            { _ ->
                val pos = MapUtils.getSpawnPosition(map)
                val yaw = MapUtils.getSpawnYaw(map)
                val spawn = PositionRotation(pos.x(), pos.y(), pos.z(), yaw, 0f)
                CompletableFuture.completedFuture(spawn)
            },
            { key ->
                changeToYetUnloadedMap(map, key)
            }
        ).await()
    }

    private fun changeToYetUnloadedMap(
        map: GameMap,
        key: ResourceKey<Level>
    ): CompletableFuture<RuntimeLevelHandle> {
        val session = (server as MinecraftServerAccessor).storageSource
        val directory = session.getDimensionPath(key)

        return CompletableFuture.runAsync { prepareMapFiles(map, directory) }
            .thenComposeAsync { _ -> loadMap(key) }
    }

    private fun prepareMapFiles(map: GameMap, directory: Path) {
        try {
            if (Files.exists(directory)) {
                FileUtils.forceDelete(directory.toFile())
            }

            mapManager.pull(map, directory)
        } catch (e: IOException) {
            throw CompletionException(e)
        }
    }

    private fun loadMap(key: ResourceKey<Level>): CompletableFuture<RuntimeLevelHandle> = server.submit<RuntimeLevelHandle> {
        KibuLevels.getInstance()
            .getWorldManager(server)
            .openPersistentLevel(key.identifier())
            .orElseThrow { IllegalStateException("Failed to load map") }
    }

    override suspend fun openRandomMap(
        gameId: Identifier,
        mapOptions: WorldOptions,
    ): Pair<ServerLevel, GameMap> {
        val map = mapRandomizer.nextMap(gameId)
        val world = changeMap(map.descriptor.identifier, mapOptions)

        setupGameLevel(world)

        return world to map
    }

    override suspend fun getMapIds(gameId: Identifier): List<Identifier> {
        return mapManager.collection()
            .mapIdsWithPrefix(gameId)
            .sorted()
            .toList()
    }

    override suspend fun getMaps(gameId: Identifier): List<GameMap> {
        return mapManager.collection()
            .mapsWithPrefix(gameId)
            .sorted(Comparator.comparing { map: GameMap ->
                map.descriptor.identifier
            })
            .toList()
    }

    override suspend fun getMap(mapId: Identifier): GameMap? {
        return mapManager.collection().getMap(mapId).orElse(null)
    }

    override suspend fun reloadMaps(gameId: Identifier) = withContext(Dispatchers.IO) {
        try {
            mapManager.loadAll(MapDescriptor(gameId))
        } catch (e: IOException) {
            throw RuntimeException("Failed to reload maps for game id $gameId", e)
        }
    }

    override fun forceMap(mapId: Identifier?) {
        mapRandomizer.forceMap(mapId)
    }
}
