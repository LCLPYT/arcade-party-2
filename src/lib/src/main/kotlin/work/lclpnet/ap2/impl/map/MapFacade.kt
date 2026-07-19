package work.lclpnet.ap2.impl.map

import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import work.lclpnet.ap2.game.util.MapReady
import work.lclpnet.gaco.asset.AssetRepository
import work.lclpnet.game.api.WorldOptions
import work.lclpnet.game.map.GameMap
import java.util.concurrent.CompletableFuture

interface MapFacade {

    val assetRepository: AssetRepository

    /**
     * Changes the current map.
     * If the new map is not yet loaded, it will be loaded first.
     * All players will be moved to the new map by default.
     * Newly joining players will be moved to the new map as well.
     * @param identifier The map id.
     * @param options The world options to specify loading behavior.
     * @return A future of the loaded map level.
     */
    fun changeMap(identifier: Identifier, options: WorldOptions): CompletableFuture<ServerLevel>

    /**
     * Opens a random map that matches a given game identifier.
     * For example, if `ap:spleef` is given, this method will open a random map for the "spleef" mini-game.
     * @param gameId The game identifier.
     * @param mapOptions The map options.
     * @return A future that completes if the map was opened.
     */
    fun openRandomMap(gameId: Identifier, mapOptions: WorldOptions): CompletableFuture<Pair<ServerLevel, GameMap>>

    fun openRandomMap(gameId: Identifier, options: WorldOptions, onReady: MapReady)

    fun getMapIds(gameId: Identifier): CompletableFuture<List<Identifier>>

    fun getMaps(gameId: Identifier): CompletableFuture<List<GameMap>>

    fun getMap(mapId: Identifier): CompletableFuture<GameMap?>

    fun reloadMaps(gameId: Identifier): CompletableFuture<Void>

    fun forceMap(mapId: Identifier?)

    fun findMapIdByPrefix(prefix: Identifier): CompletableFuture<Identifier?> {
        return getMapIds(prefix).thenApply { ids ->
            ids.firstOrNull()
        }
    }

    fun openRandomMap(gameId: Identifier, onReady: MapReady) {
        openRandomMap(gameId, WorldOptions.TEMPORARY, onReady)
    }

    /**
     * Opens a random map that matches a given game identifier.
     * For example, if `ap:spleef` is given, this method will open a random map for the "spleef" mini-game.
     * @param gameId The game identifier.
     * @return A future that completes if the map was opened.
     */
    fun openRandomMap(gameId: Identifier): CompletableFuture<Pair<ServerLevel, GameMap>> {
        return openRandomMap(gameId, WorldOptions.TEMPORARY)
    }
}
