package work.lclpnet.ap2.impl.bootstrap

import net.fabricmc.loader.api.Version
import net.fabricmc.loader.api.VersionParsingException
import net.minecraft.SharedConstants
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.api.config.Ap2Config
import work.lclpnet.ap2.api.config.ConfigManager
import work.lclpnet.ap2.api.data.DataManager
import work.lclpnet.ap2.api.map.MapFacade
import work.lclpnet.ap2.api.map.MapRandomizer
import work.lclpnet.ap2.api.music.SongManager
import work.lclpnet.ap2.impl.data.JsonDataSource
import work.lclpnet.ap2.impl.data.MapDynamicData
import work.lclpnet.ap2.impl.data.MutableDataManager
import work.lclpnet.ap2.impl.i18n.VanillaTranslations
import work.lclpnet.ap2.impl.map.MapFacadeImpl
import work.lclpnet.ap2.impl.map.SeamlessMapRandomizer
import work.lclpnet.ap2.impl.music.AssetSongManager
import work.lclpnet.ap2.util.AssetManager
import work.lclpnet.ap2.util.FontService
import work.lclpnet.ap2.util.mojang.SkinFetcher.Companion.createHttpClient
import work.lclpnet.ap2.util.mojang.SkinFetcher.Companion.sharedAssetCacheBlocking
import work.lclpnet.config.json.JsonConfigFactory
import work.lclpnet.gaco.asset.*
import work.lclpnet.gaco.asset.cache.AssetCache
import work.lclpnet.game.api.GameEnvironment
import work.lclpnet.game.api.WorldFacade
import work.lclpnet.game.map.AssetMapRepository
import work.lclpnet.game.map.MapDescriptor
import work.lclpnet.game.map.MapManager
import work.lclpnet.game.map.RepositoryMapLookup
import java.io.IOException
import java.io.InputStream
import java.net.MalformedURLException
import java.net.URI
import java.net.URL
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.ForkJoinPool

private const val CACHE_TTL_SECONDS = 3600

class ApBootstrap(
    private val configFactory: JsonConfigFactory<Ap2Config>,
    private val logger: Logger,
    private val cleanup: Cleanup
) {
    fun loadConfig(executor: Executor): CompletableFuture<ConfigManager> {
        val configPath = Path.of("config")
            .resolve(ApConstants.ID)
            .resolve("config.json")

        val configManager = ConfigManager(configPath, configFactory, logger)

        return configManager.init(executor)
            .thenApply { _ -> configManager }
    }

    fun createMapAssetRepo(config: Ap2Config, cache: AssetCache?): AssetRepository =
        createMultiAssetRepo(config.mapsSource, cache, CommonAssets.MAPS)

    fun createMapManager(assetRepo: AssetRepository): MapManager {
        val mcVersion: Version

        try {
            mcVersion = Version.parse(SharedConstants.getCurrentVersion().name())
        } catch (e: VersionParsingException) {
            throw RuntimeException(e)
        }

        val versions = mapOf(
            "minecraft" to mcVersion
        )

        val mapRepo = AssetMapRepository(assetRepo, versions, logger)

        val lookup = RepositoryMapLookup(mapRepo)

        return MapManager(lookup, logger)
    }

    private fun createMultiAssetRepo(uris: List<URI>, cache: AssetCache?, type: String): MultiAssetRepository {
        val repositories = uris
            .map { uri -> createAssetRepo(uri, cache) }
            .toTypedArray()

        check(repositories.isNotEmpty()) {
            "Asset source '$type' is empty"
        }

        return MultiAssetRepository(repositories, logger)
    }

    private fun createAssetRepo(uri: URI, cache: AssetCache?): AssetRepository {
        val repo = UriAssetRepository(uri, logger)

        // if uri is remote, use cache repository
        if (cache == null || uri.host == null) {
            return repo
        }

        val url: URL

        try {
            url = uri.toURL()
        } catch (_: MalformedURLException) {
            return repo
        }

        if ("file".equals(url.protocol, ignoreCase = true)) {
            return repo
        }

        return CacheAssetRepository(cache, repo, CACHE_TTL_SECONDS, logger)
    }

    fun createAssetCache(type: String): AssetCache? {
        try {
            return AssetCache.createUserCache(type, logger)
        } catch (e: IOException) {
            logger.error("Failed to create map cache", e)
            return null
        }
    }

    fun createMapFacade(
        server: MinecraftServer,
        mapManager: MapManager,
        worldFacade: WorldFacade,
        mapRandomizer: MapRandomizer,
        assetRepo: AssetRepository,
    ): MapFacade = MapFacadeImpl(
        worldFacade,
        mapRandomizer,
        mapManager,
        assetRepo,
        server,
        logger
    )

    fun dispatch(
        config: Ap2Config,
        environment: GameEnvironment,
        vanillaTranslations: VanillaTranslations,
        fontService: FontService
    ): CompletableFuture<Result> {
        val server = environment.server

        val mapsCache = createAssetCache(CommonAssets.MAPS)
        val songsCache = createAssetCache(ASSET_TYPE_SONGS)

        environment.whenDone {
            try {
                mapsCache?.close()
            } catch (e: Exception) {
                logger.error("Failed to close maps cache", e)
            }
            try {
                songsCache?.close()
            } catch (e: Exception) {
                logger.error("Failed to close songs cache", e)
            }
        }

        val mapAssetRepo = createMapAssetRepo(config, mapsCache)
        val mapManager = createMapManager(mapAssetRepo)
        val worldFacade = environment.worldFacade

        val randomizer = SeamlessMapRandomizer(mapManager, Random(), logger)
        val mapFacade = createMapFacade(server, mapManager, worldFacade, randomizer, mapAssetRepo)

        val songRepo: AssetRepository = createMultiAssetRepo(config.songsSource, songsCache, ASSET_TYPE_SONGS)
        val songManager = AssetSongManager(songRepo, logger)
        val dataManager = MutableDataManager()

        val mapTask = loadAp2Maps(mapManager)
        val containerTask = loadContainer(dataManager)
        val vanillaTranslationsTask = CompletableFuture.runAsync { vanillaTranslations.init() }
        val fontServiceTask = CompletableFuture.runAsync { fontService.init() }
        val assetManagerTask = CompletableFuture.supplyAsync { createAssetManagerBlocking() }

        return CompletableFuture.supplyAsync {
            mapTask.join()
            containerTask.join()
            vanillaTranslationsTask.join()
            fontServiceTask.join()

            val assetManager = assetManagerTask.join()

            Result(worldFacade, mapFacade, songManager, dataManager, assetManager)
        }
    }

    private fun createAssetManagerBlocking(): AssetManager {
        val httpClient = createHttpClient()

        cleanup.whenDone { httpClient.close() }

        val mojangAssetCache = sharedAssetCacheBlocking(logger)

        cleanup.whenDone {
            try {
                mojangAssetCache.close()
            } catch (e: Exception) {
                logger.error("Failed to close mojang asset cache", e)
            }
        }

        return AssetManager(httpClient, mojangAssetCache)
    }

    fun loadAp2Maps(mapManager: MapManager): CompletableFuture<Void> =
        loadMaps(
            mapManager,
            MapDescriptor(ApConstants.ID, ""),
            ForkJoinPool.commonPool()
        )

    fun loadMaps(mapManager: MapManager, descriptor: MapDescriptor, executor: Executor): CompletableFuture<Void> =
        CompletableFuture.runAsync({
            try {
                // load general arcade party 2 maps
                mapManager.loadAll(descriptor)
            } catch (e: IOException) {
                throw RuntimeException("Failed to load maps of namespace ${ApConstants.ID}", e)
            }
        }, executor)

    fun loadContainer(dataManager: MutableDataManager): CompletableFuture<Void> {
        return CompletableFuture.runAsync {
            dataManager.setData(
                MapDynamicData.builder()
                    .addSource(JsonDataSource({ openConfigurationFile() }, logger))
                    .build()
            )
        }
    }

    fun openConfigurationFile(): InputStream =
        requireNotNull(javaClass.getResourceAsStream("/configuration.json")) {
            "File not found: configuration.json"
        }

    data class Result(
        val worldFacade: WorldFacade,
        val mapFacade: MapFacade,
        val songManager: SongManager,
        val dataManager: DataManager,
        val assetManager: AssetManager
    )

    fun interface Cleanup {
        fun whenDone(action: Runnable)
    }

    companion object {
        const val ASSET_TYPE_SONGS: String = "songs"
    }
}
