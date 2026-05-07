package work.lclpnet.ap2.util

import io.ktor.client.HttpClient
import work.lclpnet.gaco.asset.cache.AssetCache

class AssetManager(
    val httpClient: HttpClient,
    val mojangAssetCache: AssetCache,
)