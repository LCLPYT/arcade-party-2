package work.lclpnet.ap2.game.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.gaco.asset.AssetPath
import work.lclpnet.game.map.GameMap
import work.lclpnet.kibu.schematic.FabricBlockStateAdapter
import work.lclpnet.kibu.schematic.SchematicFormats
import work.lclpnet.kibu.structure.BlockStructure
import java.io.IOException
import java.io.InputStream

fun GameMap.assetPath(path: String): AssetPath {
    val mapPath = descriptor.getMapPath()

    return AssetPath.of(mapPath, path)
}

@Throws(IOException::class)
fun MiniGameHandle.asset(path: AssetPath): InputStream {
    return mapFacade.assetRepository.getStream(path).resource()
}

suspend fun MiniGameHandle.schematic(path: AssetPath): BlockStructure = withContext(Dispatchers.IO) {
    asset(path).use { input ->
        SchematicFormats.SPONGE_V2.reader().read(input, FabricBlockStateAdapter.getInstance())
    }
}
