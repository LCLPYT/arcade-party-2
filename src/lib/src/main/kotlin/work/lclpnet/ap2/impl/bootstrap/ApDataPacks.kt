package work.lclpnet.ap2.impl.bootstrap

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.future.future
import kotlinx.coroutines.withContext
import net.minecraft.resources.Identifier
import org.apache.commons.io.FileUtils
import org.slf4j.Logger
import work.lclpnet.ap2.api.config.Ap2Config
import work.lclpnet.config.json.JsonConfigFactory
import work.lclpnet.gaco.asset.CommonAssets
import work.lclpnet.game.api.data.DataPackSink
import work.lclpnet.game.api.data.GameDataPacks
import work.lclpnet.game.map.GameMap
import work.lclpnet.game.map.MapDescriptor
import work.lclpnet.game.map.MapManager
import java.io.IOException
import java.lang.AutoCloseable
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.stream.Stream

class ApDataPacks(
    private val cacheDirectory: Path,
    private val configFactory: JsonConfigFactory<Ap2Config>,
    private val logger: Logger
) : GameDataPacks {

    override fun downloadPacks(dataPackSink: DataPackSink, executor: Executor): CompletableFuture<Void?> {
        return CoroutineScope(executor.asCoroutineDispatcher()).future {
            val cleanup = ArrayList<Runnable>()
            val resources = ArrayList<AutoCloseable>()

            try {
                downloadPacksAsync(dataPackSink, cleanup, resources)
            } catch (e: Throwable) {
                logger.error("Failed to locate data packs", e)
                throw e
            } finally {
                for (resource in resources) {
                    try {
                        resource.close()
                    } catch (e: Throwable) {
                        logger.error("Failed to close resource {}", resource, e)
                    }
                }

                for (action in cleanup) {
                    try {
                        action.run()
                    } catch (e: Throwable) {
                        logger.error("Failed to cleanup", e)
                    }
                }
            }

            null
        }
    }

    private suspend fun downloadPacksAsync(
        dataPackSink: DataPackSink,
        cleanup: MutableList<Runnable>,
        resources: MutableList<AutoCloseable>
    ) {
        val bootstrap = ApBootstrap(configFactory, logger) { task -> cleanup.add(task) }
        val dataPacksPath = requireNotNull(Identifier.fromNamespaceAndPath("datapacks", ""))

        val configManager = bootstrap.loadConfig()
        val config = configManager.config
        val cache = bootstrap.createAssetCache(CommonAssets.MAPS)
        val repo = bootstrap.createMapAssetRepo(config, cache)
        val mapManager = bootstrap.createMapManager(repo)

        cache?.let { resources.add(it) }

        bootstrap.loadMaps(mapManager, MapDescriptor(dataPacksPath))

        val maps = mapManager.collection().mapsWithPrefix(dataPacksPath)

        withContext(Dispatchers.IO) {
            fetchDataPacks(mapManager, maps, dataPackSink)
        }
    }

    private fun fetchDataPacks(mapManager: MapManager, maps: Stream<GameMap>, sink: DataPackSink) {
        val it = maps.iterator()

        val dir = cacheDirectory.resolve("data_pack_maps")

        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir)
            } catch (e: IOException) {
                logger.error("Failed to create directory: {}", dir, e)
                return
            }
        }

        while (it.hasNext()) {
            val map = it.next()

            val directory = dir.resolve(map.descriptor.getMapPath())

            try {
                if (Files.exists(directory)) {
                    FileUtils.forceDelete(directory.toFile())
                }

                Files.createDirectories(dir.parent)

                mapManager.pull(map, directory)

                offerPacksFrom(directory, sink)
            } catch (e: IOException) {
                logger.error("Failed fetch data packs of map {}: failed to pull", map, e)
            }
        }
    }

    @Throws(IOException::class)
    private fun offerPacksFrom(directory: Path, sink: DataPackSink) {
        val packsDir = directory.resolve("datapacks")

        if (!Files.isDirectory(packsDir)) return

        val packs: List<Path>

        Files.list(packsDir).use { files ->
            packs = files.filter { it.fileName.toString().endsWith(".zip") }
                .filter { Files.isRegularFile(it) }
                .toList()
        }

        for (pack in packs) {
            try {
                Files.newInputStream(pack).use { input ->
                    sink.offer(pack.fileName, input)
                }
            } catch (e: IOException) {
                logger.error("Failed to copy data pack {}", pack, e)
            }
        }
    }
}
