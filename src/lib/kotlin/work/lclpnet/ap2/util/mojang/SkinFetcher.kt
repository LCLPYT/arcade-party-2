package work.lclpnet.ap2.util.mojang

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.future.future
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import work.lclpnet.ap2.toUndashedString
import work.lclpnet.gaco.asset.AssetPath
import work.lclpnet.gaco.asset.cache.AssetCache
import work.lclpnet.gaco.asset.cache.SqliteCacheIndex
import work.lclpnet.kibu.assets.OsUtil
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO
import kotlin.io.path.*

private const val PROFILE_CACHE_SECONDS = 60 * 60 * 24  // 1d

class SkinFetcher(
    private val client: HttpClient,
    private val assetCache: AssetCache,
    val skinAssetsDir: Path,
    private val logger: Logger,
) {
    companion object {
        @JvmStatic
        fun createHttpClient() = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                })
            }
        }

        /**
         * Java API for creating a shared asset cache for the mojang api.
         */
        @OptIn(DelicateCoroutinesApi::class)
        @JvmStatic
        fun sharedAssetCacheAsync(logger: Logger): CompletableFuture<AssetCache> {
            return GlobalScope.future { sharedAssetCache(logger) }
        }

        suspend fun sharedAssetCache(logger: Logger): AssetCache {
            val root = OsUtil.getCacheDir().resolve("kibu").resolve("mojang_api")

            val index = withContext(Dispatchers.IO) {
                Files.createDirectories(root)

                val indexPath = root.resolve("index.sqlite")

                SqliteCacheIndex.createSqliteIndex(indexPath, logger)
            }

            return AssetCache(
                index,
                root,
                logger
            )
        }

        @JvmStatic
        fun sharedSkinDirectory(): Path = OsUtil.getCacheDir().resolve("kibu").resolve("mc_assets")
            .resolve("skins")

        @JvmStatic
        suspend fun loadDefaultSkin(): BufferedImage? = withContext(Dispatchers.IO) {
            val url = requireNotNull(this::class.java.getResource("/steve.png")) {
                "Default skin texture not found"
            }

            ImageIO.read(url)
        }

        @JvmStatic
        fun getFaceTexture(skinImage: BufferedImage): BufferedImage {
            val face = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)

            val graphics = face.createGraphics()

            // face texture
            graphics.drawImage(
                skinImage.getSubimage(8, 8, 8, 8),
                0,
                0,
                8,
                8,
                Color.WHITE,
                null
            )

            // hat texture
            graphics.drawImage(
                skinImage.getSubimage(40, 8, 8, 8),
                0,
                0,
                8,
                8,
                Color(0, true),
                null
            )

            graphics.dispose()

            return face
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val inFlight = mutableMapOf<UUID, Deferred<BufferedImage?>>()
    private val inFlightMutex = Mutex()
    private val defaultSkinMutex = Mutex()
    private var defaultSkin: BufferedImage? = null

    suspend fun defaultSkin(): BufferedImage? =
        defaultSkin ?: defaultSkinMutex.withLock {
            defaultSkin ?: loadDefaultSkin()?.also { defaultSkin = it }
        }

    suspend fun fetchSkin(uuid: UUID): BufferedImage? {
        logger.debug("Requested skin for player with uuid {}", uuid)

        return executeWithLock(uuid) {
            fetchSkinInternal(uuid)
        }
    }

    suspend fun fetchSkin(profile: Profile): BufferedImage? {
        logger.debug("Requested skin for profile {}", profile.id)

        return executeWithLock(profile.id) {
            loadSkinCatching(profile) ?: defaultSkin()
        }
    }

    private suspend fun executeWithLock(
        uuid: UUID,
        action: suspend () -> BufferedImage?
    ): BufferedImage? {
        val deferred = inFlightMutex.withLock {
            inFlight[uuid] ?: scope.async {
                try {
                    action()
                } finally {
                    @Suppress("DeferredResultUnused")
                    inFlightMutex.withLock {
                        inFlight.remove(uuid)
                    }
                }
            }.also { inFlight[uuid] = it }
        }

        return deferred.await()
    }

    private suspend fun fetchSkinInternal(uuid: UUID): BufferedImage? {
        val result = try {
            loadProfileCachedOrFetch(uuid)
        } catch (err: Throwable) {
            if (err is CancellationException) throw err

            logger.error("Failed to fetch profile of player {}, using default skin", uuid)

            return defaultSkin()
        } ?: return defaultSkin()

        val skin = loadSkinCatching(result.value)

        if (skin != null) {
            return skin
        }

        if (!result.mayRefetch) {
            logger.debug("Failed to load skin of player {}, using default skin", uuid)

            return defaultSkin()
        }

        // profile was from cache, retry with fresh profile
        logger.debug("Failed to load skin of player {}, trying to fetch fresh profile...", uuid)

        val freshProfile = try {
            fetchFreshProfile(uuid)
        } catch (err: Throwable) {
            if (err is CancellationException) throw err

            logger.error("Failed to fetch fresh profile of player {}", uuid, err)

            return defaultSkin()
        } ?: return defaultSkin()

        return loadSkinCatching(freshProfile) ?: defaultSkin()
    }

    private suspend fun loadSkinCatching(profile: Profile): BufferedImage? = try {
        loadSkin(profile)
    } catch (err: Throwable) {
        if (err is CancellationException) throw err

        logger.error("Failed to load skin of profile {}", profile, err)

        null
    }

    private data class FetchResult<T>(
        val value: T,
        val mayRefetch: Boolean,
    )

    private suspend fun loadProfileCachedOrFetch(uuid: UUID): FetchResult<Profile>? {
        val undashed = uuid.toUndashedString()
        val profileAsset = AssetPath.of("profile", undashed)

        logger.debug("Checking if profile {} exists in cache", undashed)

        val cached: AssetCache.CacheInfo? = withContext(Dispatchers.IO) {
            assetCache.getCacheInfo(profileAsset).orElse(null)
        }

        if (cached != null && cached.valid && cached.path.exists()) {
            // try to load cached profile
            val profile = loadProfile(cached.path)

            if (profile != null) {
                logger.debug("Using profile {} from cache", undashed)
                return FetchResult(profile, true)
            }
        }

        try {
            val profile = fetchFreshProfile(uuid) ?: return null

            return FetchResult(profile, false)
        } catch (err: Throwable) {
            if (err is CancellationException) throw err

            if (cached != null) {
                logger.error("Failed to fetch profile of player {}", undashed, err)
                logger.debug("Falling back to cached skin for player {}", undashed)

                val profile = loadProfile(cached.path) ?: return null

                return FetchResult(profile, false)
            }

            throw IOException("Failed to fetch profile of player $undashed", err)
        }
    }

    private suspend fun fetchFreshProfile(uuid: UUID): Profile? {
        val undashed = uuid.toUndashedString()
        val profileAsset = AssetPath.of("profile", undashed)

        logger.debug("Fetching profile {} ...", uuid)

        val res = client.get("https://sessionserver.mojang.com/session/minecraft/profile/$undashed")

        if (res.status == HttpStatusCode.NoContent) {
            logger.debug("No profile exists for uuid {}", undashed)
            return null
        }

        if (res.status != HttpStatusCode.OK) {
            throw IOException("Got invalid status '${res.status}' for profile of $undashed")
        }

        val profile: Profile = res.body()

        val json = Json.encodeToString(profile)

        withContext(Dispatchers.IO) {
            try {
                assetCache.cache(
                    profileAsset,
                    json.toByteArray(StandardCharsets.UTF_8).inputStream(),
                    PROFILE_CACHE_SECONDS
                )
            } catch (e: IOException) {
                logger.error("Failed to cache profile {}", undashed, e)
            }
        }

        logger.debug("Profile {} successfully fetched", undashed)

        return profile
    }

    private suspend fun loadProfile(path: Path): Profile? {
        return try {
            val json = withContext(Dispatchers.IO) {
                path.readText(StandardCharsets.UTF_8)
            }

            return Json.decodeFromString<Profile>(json)
        } catch(err: Throwable) {
            if (err is CancellationException) throw err

            logger.error("Failed to read profile at {}", path, err)

            null
        }
    }

    fun skinPath(skinId: String): Path = when {
        skinId.length < 2 -> skinAssetsDir.resolve(skinId)
        else -> skinAssetsDir.resolve(skinId.substring(0, 2)).resolve(skinId)
    }

    private suspend fun loadSkin(profile: Profile): BufferedImage? {
        logger.debug("Loading skin of profile {}", profile.id)

        val skinUrl = profile.texturesProperty?.value?.skinTexture?.url ?: return null

        val regex = Regex("^http://textures\\.minecraft\\.net/texture/([a-z0-9]+)$")
        val match = regex.matchEntire(skinUrl) ?: return null

        val (skinId) = match.destructured

        val path = skinPath(skinId)

        logger.debug("Checking if skin {} exists in cache...", skinId)

        val cachedImage = withContext(Dispatchers.IO) {
            if (path.isRegularFile()) {
                loadImage(path)
            } else {
                null
            }
        }

        if (cachedImage != null) {
            logger.debug("Using skin {} from cache", skinId)
            return cachedImage
        }

        logger.debug("Fetching skin {} ...", skinId)

        val skinResponse = client.get(skinUrl)

        if (!skinResponse.status.isSuccess()) {
            throw IOException("Got invalid status '${skinResponse.status}' for skin at $skinUrl")
        }

        val image = withContext(Dispatchers.IO) {
            path.parent?.createDirectories()

            path.outputStream().use {
                skinResponse.bodyAsChannel().copyTo(it)
            }

            loadImage(path)
        }

        if (image != null) {
            logger.debug("Skin {} successfully fetched", skinId)
        }

        return image
    }

    private fun loadImage(path: Path): BufferedImage? = try {
        path.inputStream().use {
            ImageIO.read(it)
        }
    } catch (err: Throwable) {
        if (err is CancellationException) throw err

        logger.error("Failed to read image at {}", path, err)

        null
    }
}