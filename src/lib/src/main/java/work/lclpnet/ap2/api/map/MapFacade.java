package work.lclpnet.ap2.api.map;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.MapReady;
import work.lclpnet.gaco.asset.AssetRepository;
import work.lclpnet.game.api.WorldOptions;
import work.lclpnet.game.map.GameMap;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface MapFacade {

    /**
     * Changes the current map.
     * If the new map is not yet loaded, it will be loaded first.
     * All players will be moved to the new map by default.
     * Newly joining players will be moved to the new map as well.
     * @param identifier The map id.
     * @param options The world options to specify loading behavior.
     * @return A future of the loaded map level.
     */
    CompletableFuture<ServerLevel> changeMap(Identifier identifier, WorldOptions options);

    /**
     * Opens a random map that matches a given game identifier.
     * For example, if <code>ap:spleef</code> is given, this method will open a random map for the "spleef" mini-game.
     * @param gameId The game identifier.
     * @param mapOptions The map options.
     * @return A future that completes if the map was opened.
     */
    CompletableFuture<Pair<ServerLevel, GameMap>> openRandomMap(Identifier gameId, WorldOptions mapOptions);

    void openRandomMap(Identifier gameId, WorldOptions options, MapReady onReady);

    CompletableFuture<List<Identifier>> getMapIds(Identifier gameId);

    CompletableFuture<List<GameMap>> getMaps(Identifier gameId);

    CompletableFuture<Optional<GameMap>> getMap(Identifier mapId);

    CompletableFuture<Void> reloadMaps(Identifier gameId);

    void forceMap(@Nullable Identifier mapId);

    AssetRepository getAssetRepository();

    default CompletableFuture<Optional<Identifier>> findMapIdByPrefix(Identifier prefix) {
        return getMapIds(prefix).thenApply(ids -> ids.stream().findFirst());
    }

    default void openRandomMap(Identifier gameId, MapReady onReady) {
        openRandomMap(gameId, WorldOptions.TEMPORARY, onReady);
    }

    /**
     * Opens a random map that matches a given game identifier.
     * For example, if <code>ap:spleef</code> is given, this method will open a random map for the "spleef" mini-game.
     * @param gameId The game identifier.
     * @return A future that completes if the map was opened.
     */
    default CompletableFuture<Pair<ServerLevel, GameMap>> openRandomMap(Identifier gameId) {
        return openRandomMap(gameId, WorldOptions.TEMPORARY);
    }
}
