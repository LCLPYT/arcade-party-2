package work.lclpnet.ap2.util.mojang

import io.ktor.client.*
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import work.lclpnet.gaco.asset.cache.AssetCache
import work.lclpnet.gaco.asset.cache.SqliteCacheIndex
import java.nio.file.Files
import java.util.*

class SkinFetcherTest {

    companion object {
        @JvmStatic
        @BeforeAll
        @Throws(ClassNotFoundException::class)
        fun setupSqlite() {
            Class.forName("org.sqlite.JDBC", true, SqliteCacheIndex::class.java.getClassLoader())
        }
    }

    val logger: Logger = LoggerFactory.getLogger(SkinFetcherTest::class.java)
    var client: HttpClient? = null
    var assetCache: AssetCache? = null
    var skinFetcher: SkinFetcher? = null

    @BeforeEach
    fun setup() {
        val dir = Files.createTempDirectory("ap2_skin_fetcher")
        logger.info("Using $dir as content root")

        client = SkinFetcher.createHttpClient()

        assetCache = AssetCache(
            SqliteCacheIndex.createSqliteIndex(dir!!.resolve("index.sqlite"), logger),
            dir,
            logger
        )

        skinFetcher = SkinFetcher(
            client!!,
            assetCache!!,
            dir.resolve("skins"),
            logger
        )
    }

    @AfterEach
    fun teardown() {
        client!!.close()
        assetCache!!.close()
    }

    @Test
    fun fetchSkin_existing_uncached() {
        runBlocking {
            val defaultSkin = async { skinFetcher!!.defaultSkin() }
            val skin = skinFetcher!!.fetchSkin(UUID.fromString("7357a549-fa3e-4342-91b2-63e5e73ed39a"))

            assertNotNull(skin)
            assertNotSame(defaultSkin, skin)
        }
    }

    @Test
    fun fetchSkin_notExisting_defaultSkinIsUsed() {
        runBlocking {
            val defaultSkin = async { skinFetcher!!.defaultSkin() }
            val skin = skinFetcher!!.fetchSkin(UUID.randomUUID())

            assertNotNull(skin)
            assertSame(defaultSkin.await(), skin)
        }
    }

    @Test
    fun fetchSkin_concurrent() {
        val uuid = UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6")

        runBlocking {
            val first = async { skinFetcher!!.fetchSkin(uuid) }
            val second = async { skinFetcher!!.fetchSkin(uuid) }

            val firstSkin = first.await()
            val secondSkin = second.await()

            assertNotNull(firstSkin)
            assertSame(firstSkin, secondSkin)
        }
    }
}