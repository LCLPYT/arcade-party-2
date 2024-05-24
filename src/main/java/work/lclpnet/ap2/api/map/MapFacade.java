package work.lclpnet.ap2.api.map;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.game.MapReady;
import work.lclpnet.lobby.game.api.MapOptions;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface MapFacade {

    /**
     * Opens a random map that matches a given game identifier.
     * For example, if <code>ap:spleef</code> is given, this method will open a random map for the "spleef" mini-game.
     * @param gameId The game identifier.
     * @param mapOptions The map options.
     * @return A future that completes if the map was opened.
     */
    CompletableFuture<Pair<ServerWorld, GameMap>> openRandomMap(Identifier gameId, MapOptions mapOptions);

    void openRandomMap(Identifier gameId, MapOptions options, MapReady onReady);

    CompletableFuture<Set<Identifier>> getMapIds(Identifier gameId);

    CompletableFuture<Set<GameMap>> getMaps(Identifier gameId);

    CompletableFuture<Void> reloadMaps(Identifier gameId);

    void forceMap(Identifier mapId);

    default void openRandomMap(Identifier gameId, MapReady onReady) {
        openRandomMap(gameId, MapOptions.TEMPORARY, onReady);
    }

    /**
     * Opens a random map that matches a given game identifier.
     * For example, if <code>ap:spleef</code> is given, this method will open a random map for the "spleef" mini-game.
     * @param gameId The game identifier.
     * @return A future that completes if the map was opened.
     */
    default CompletableFuture<Pair<ServerWorld, GameMap>> openRandomMap(Identifier gameId) {
        return openRandomMap(gameId, MapOptions.TEMPORARY);
    }
}
