package work.lclpnet.ap2.game.util

import kotlinx.coroutines.suspendCancellableCoroutine
import net.minecraft.server.level.ServerLevel
import work.lclpnet.ap2.api.game.team.TeamManager
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.game.map.GameMap
import kotlin.coroutines.resume

suspend fun MiniGameHandle.openRandomMap(): Pair<ServerLevel, GameMap> {
    return suspendCancellableCoroutine { continuation ->
        mapFacade.openRandomMap(gameInfo.id) { level, map ->
            // executed on the server thread
            setWorld(level)

            continuation.resume(level to map)
        }
    }
}

fun interface MapLevelInstanceInit {
    fun createInstance(handle: MiniGameHandle, level: ServerLevel, map: GameMap): MiniGameInstance
}

fun interface MapLevelTeamInstanceInit {
    fun createInstance(handle: MiniGameHandle, level: ServerLevel, map: GameMap, teamManager: TeamManager): MiniGameInstance
}

class MapLevelGameFactory(val instanceFactory: MapLevelInstanceInit) : MiniGameFactory {

    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val (level, map) = handle.openRandomMap()

        return instanceFactory.createInstance(handle, level, map)
    }
}

class MapLevelTeamGameFactory(val instanceFactory: MapLevelTeamInstanceInit) : MiniGameFactory {

    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val (level, map) = handle.openRandomMap()
        val teamManager = handle.createTeamManager()

        return instanceFactory.createInstance(handle, level, map, teamManager)
    }
}