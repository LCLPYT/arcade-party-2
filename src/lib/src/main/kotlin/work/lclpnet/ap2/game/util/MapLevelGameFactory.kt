package work.lclpnet.ap2.game.util

import kotlinx.coroutines.suspendCancellableCoroutine
import net.minecraft.server.level.ServerLevel
import work.lclpnet.ap2.api.game.team.TeamManager
import work.lclpnet.ap2.game.MiniGameFactory
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.impl.map.schema.MapSchemaLoader
import work.lclpnet.game.map.GameMap
import work.lclpnet.map_api.GameMapApi
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun MiniGameHandle.openRandomMap(): Pair<ServerLevel, GameMap> {
    return suspendCancellableCoroutine { continuation ->
        mapFacade.openRandomMap(gameInfo.id) { level, map ->
            // executed on the server thread
            setWorld(level)

            continuation.resume(level to map)
        }
    }
}

/**
 * Awaits the asynchronously loaded map data of the given level and loads a map schema from it.
 * Should be called from within a [MiniGameFactory] after the map has been opened.
 */
suspend fun <T : Any> MiniGameHandle.loadSchema(level: ServerLevel, schemaClass: Class<T>): T {
    val worldData = suspendCancellableCoroutine { continuation ->
        GameMapApi.get(server).dataManager
            .awaitWorldData(level.dimension())
            .whenComplete { data, err ->
                if (err != null) continuation.resumeWithException(err)
                else continuation.resume(data)
            }
    }

    val schema = MapSchemaLoader(logger).load(worldData, schemaClass)

    return requireNotNull(schema) {
        "Failed to load map schema ${schemaClass.name}, look for any previous errors"
    }
}

fun interface MapLevelInstanceInit {
    fun createInstance(handle: MiniGameHandle, level: ServerLevel, map: GameMap): MiniGameInstance
}

fun interface MapLevelTeamInstanceInit {
    fun createInstance(handle: MiniGameHandle, level: ServerLevel, map: GameMap, teamManager: TeamManager): MiniGameInstance
}

fun interface MapLevelSchemaInstanceInit<S> {
    fun createInstance(handle: MiniGameHandle, level: ServerLevel, map: GameMap, schema: S): MiniGameInstance
}

fun interface MapLevelTeamSchemaInstanceInit<S> {
    fun createInstance(handle: MiniGameHandle, level: ServerLevel, map: GameMap, teamManager: TeamManager, schema: S): MiniGameInstance
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

class MapLevelSchemaGameFactory<S : Any>(
    val schemaClass: Class<S>,
    val instanceFactory: MapLevelSchemaInstanceInit<S>
) : MiniGameFactory {

    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val (level, map) = handle.openRandomMap()
        val schema = handle.loadSchema(level, schemaClass)

        return instanceFactory.createInstance(handle, level, map, schema)
    }
}

class MapLevelTeamSchemaGameFactory<S : Any>(
    val schemaClass: Class<S>,
    val instanceFactory: MapLevelTeamSchemaInstanceInit<S>
) : MiniGameFactory {

    override suspend fun createInstance(handle: MiniGameHandle): MiniGameInstance {
        val (level, map) = handle.openRandomMap()
        val teamManager = handle.createTeamManager()
        val schema = handle.loadSchema(level, schemaClass)

        return instanceFactory.createInstance(handle, level, map, teamManager, schema)
    }
}