package work.lclpnet.ap2.game

import kotlinx.coroutines.suspendCancellableCoroutine
import net.minecraft.server.level.ServerLevel
import work.lclpnet.game.map.GameMap
import kotlin.coroutines.resume

fun interface MapLevelInstanceInit {
    fun createInstance(handle: MiniGameHandle, level: ServerLevel, map: GameMap): MiniGameInstance
}

class MapLevelGameFactory(val instanceFactory: MapLevelInstanceInit) : MiniGameFactory {

    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        return suspendCancellableCoroutine { continuation ->

            handle.mapFacade.openRandomMap(handle.gameInfo.id) { level, map ->
                // executed on the server thread
                val instance = instanceFactory.createInstance(handle, level, map)

                continuation.resume(instance)
            }
        }
    }
}