package work.lclpnet.ap2.api.map;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.game.MapReady;
import work.lclpnet.gaco.asset.AssetRepository;
import work.lclpnet.lobby.game.api.MapOptions;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface MapFacade {

    /**
     * Opens a random map that matches a given game identifier.
     * For example, if <code>ap:spleef</code> is given, this method will open a random map for the "spleef" mini-game.
     * @param gameId The game identifier.
     * @param mapOptions The map options.
     * @return A future that completes if the map was opened.
     */
    CompletableFuture<Pair<ServerLevel, GameMap>> openRandomMap(ResourceLocation gameId, MapOptions mapOptions);

    void openRandomMap(ResourceLocation gameId, MapOptions options, MapReady onReady);

    CompletableFuture<List<ResourceLocation>> getMapIds(ResourceLocation gameId);

    CompletableFuture<List<GameMap>> getMaps(ResourceLocation gameId);

    CompletableFuture<Optional<GameMap>> getMap(ResourceLocation mapId);

    CompletableFuture<Void> reloadMaps(ResourceLocation gameId);

    void forceMap(@Nullable ResourceLocation mapId);

    AssetRepository getAssetRepository();

    default void openRandomMap(ResourceLocation gameId, MapReady onReady) {
        openRandomMap(gameId, MapOptions.TEMPORARY, onReady);
    }

    /**
     * Opens a random map that matches a given game identifier.
     * For example, if <code>ap:spleef</code> is given, this method will open a random map for the "spleef" mini-game.
     * @param gameId The game identifier.
     * @return A future that completes if the map was opened.
     */
    default CompletableFuture<Pair<ServerLevel, GameMap>> openRandomMap(ResourceLocation gameId) {
        return openRandomMap(gameId, MapOptions.TEMPORARY);
    }
}
