package work.lclpnet.ap2.impl.map

import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import work.lclpnet.gaco.asset.AssetRepository
import work.lclpnet.game.api.WorldOptions
import work.lclpnet.game.map.GameMap

interface MapFacade {

    val assetRepository: AssetRepository

    /**
     * Changes the current map.
     * If the new map is not yet loaded, it will be loaded first.
     * All players will be moved to the new map by default.
     * Newly joining players will be moved to the new map as well.
     * @param identifier The map id.
     * @param options The world options to specify loading behavior.
     * @return The loaded map level.
     */
    suspend fun changeMap(identifier: Identifier, options: WorldOptions): ServerLevel

    /**
     * Opens a random map that matches a given game identifier.
     * For example, if `ap:spleef` is given, this method will open a random map for the "spleef" mini-game.
     * @param gameId The game identifier.
     * @param mapOptions The map options.
     * @return The opened map level and its map data.
     */
    suspend fun openRandomMap(gameId: Identifier, mapOptions: WorldOptions): Pair<ServerLevel, GameMap>

    suspend fun getMapIds(gameId: Identifier): List<Identifier>

    suspend fun getMaps(gameId: Identifier): List<GameMap>

    suspend fun getMap(mapId: Identifier): GameMap?

    suspend fun reloadMaps(gameId: Identifier)

    fun forceMap(mapId: Identifier?)

    suspend fun findMapIdByPrefix(prefix: Identifier): Identifier? {
        return getMapIds(prefix).firstOrNull()
    }

    /**
     * Opens a random map that matches a given game identifier.
     * For example, if `ap:spleef` is given, this method will open a random map for the "spleef" mini-game.
     * @param gameId The game identifier.
     * @return The opened map level and its map data.
     */
    suspend fun openRandomMap(gameId: Identifier): Pair<ServerLevel, GameMap> {
        return openRandomMap(gameId, WorldOptions.TEMPORARY)
    }
}
