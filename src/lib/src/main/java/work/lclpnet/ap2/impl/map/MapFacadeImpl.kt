package work.lclpnet.ap2.impl.map

import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import org.apache.commons.io.FileUtils
import org.slf4j.Logger
import work.lclpnet.ap2.api.map.MapFacade
import work.lclpnet.ap2.api.map.MapRandomizer
import work.lclpnet.ap2.game.util.MapReady
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

    override fun changeMap(identifier: Identifier, options: WorldOptions): CompletableFuture<ServerLevel> {
        val optMap = mapManager.collection().getMap(identifier)

        if (optMap.isEmpty) {
            return CompletableFuture.failedFuture(IllegalStateException("Unknown map $identifier"))
        }

        val map = optMap.get()

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
        )
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

    override fun openRandomMap(
        gameId: Identifier,
        mapOptions: WorldOptions,
    ): CompletableFuture<Pair<ServerLevel, GameMap>> = mapRandomizer.nextMap(gameId)
        .thenCompose { map: GameMap ->
            val id = map.descriptor.identifier
            changeMap(id, mapOptions).thenApply { world ->
                world to map
            }
        }
        .thenApply { pair ->
            setupGameLevel(pair.first)
            pair
        }

    override fun openRandomMap(gameId: Identifier, options: WorldOptions, onReady: MapReady) {
        openRandomMap(gameId, options)
            .thenCompose { (level, map) ->
                server.submit {
                    onReady.onReady(level, map)
                }
            }
            .exceptionally { throwable ->
                logger.error("Failed to open a random map for game {}", gameId, throwable)
                null
            }
    }

    override fun getMapIds(gameId: Identifier): CompletableFuture<List<Identifier>> {
        val mapIds = mapManager.collection()
            .mapIdsWithPrefix(gameId)
            .sorted()
            .toList()

        return CompletableFuture.completedFuture(mapIds)
    }

    override fun getMaps(gameId: Identifier): CompletableFuture<List<GameMap>> {
        val maps = mapManager.collection()
            .mapsWithPrefix(gameId)
            .sorted(Comparator.comparing { map: GameMap ->
                map.descriptor.identifier
            })
            .toList()

        return CompletableFuture.completedFuture(maps)
    }

    override fun getMap(mapId: Identifier): CompletableFuture<GameMap?> {
        val optMap = mapManager.collection().getMap(mapId).orElse(null)

        return CompletableFuture.completedFuture(optMap)
    }

    override fun reloadMaps(gameId: Identifier): CompletableFuture<Void> {
        return CompletableFuture.runAsync {
            try {
                mapManager.loadAll(MapDescriptor(gameId))
            } catch (e: IOException) {
                throw RuntimeException("Failed to reload maps for game id $gameId", e)
            }
        }
    }

    override fun forceMap(mapId: Identifier?) {
        mapRandomizer.forceMap(mapId)
    }
}
