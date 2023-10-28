package work.lclpnet.ap2.impl.map;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.map.MapFrequencyManager;
import work.lclpnet.ap2.api.map.MapRandomizer;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapDescriptor;
import work.lclpnet.lobby.game.map.MapManager;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link MapRandomizer} that takes the amount of times the map was picked into account.
 * The goal of this randomizer is that every map should be played approximately equally often.
 */
public class BalancedMapRandomizer implements MapRandomizer {

    private final MapManager mapManager;
    private final MapFrequencyManager frequencyTracker;
    private final Random random;

    public BalancedMapRandomizer(MapManager mapManager, MapFrequencyManager frequencyTracker, Random random) {
        this.mapManager = mapManager;
        this.frequencyTracker = frequencyTracker;
        this.random = random;
    }

    @Override
    public CompletableFuture<GameMap> nextMap(Identifier gameId) {
        String prefix = withSlash(gameId);

        return reloadMaps(gameId)
                .thenCompose(nil -> chooseRandomMap(prefix));
    }

    private CompletableFuture<Void> reloadMaps(Identifier gameId) {
        return CompletableFuture.runAsync(() -> {
            try {
                mapManager.loadAll(new MapDescriptor(gameId, ApConstants.MAP_VERSION));
            } catch (IOException e) {
                throw new RuntimeException("Failed to reload maps for game id %s".formatted(gameId), e);
            }
        });
    }

    private CompletableFuture<GameMap> chooseRandomMap(String prefix) {
        var mapIds = mapManager.getCollection().getMaps().stream()
                .map(GameMap::getDescriptor)
                .map(MapDescriptor::getIdentifier)
                .filter(id -> id.getPath().startsWith(prefix))
                .distinct().toList();

        if (frequencyTracker instanceof AsyncMapFrequencyManager async) {
            return async.preload(mapIds).thenCompose(ignored -> getRandomMap(mapIds));
        }

        return CompletableFuture.completedFuture(mapIds).thenCompose(this::getRandomMap);
    }

    private CompletableFuture<GameMap> getRandomMap(List<Identifier> mapIds) {
        var mapFrequencies = mapIds.stream()
                .map(id -> Pair.of(id, frequencyTracker.getFrequency(id)))
                .toList();

        if (mapFrequencies.isEmpty()) {
            return CompletableFuture.failedFuture(new NoSuchElementException("No maps found"));
        }

        long minFrequency = mapFrequencies.stream()
                .mapToLong(Pair::right)
                .min()
                .orElseThrow();

        var leastPlayedMaps = mapFrequencies.stream()
                .filter(pair -> pair.right() == minFrequency)
                .map(Pair::left)
                .toList();

        Identifier randomMapId = leastPlayedMaps.get(random.nextInt(leastPlayedMaps.size()));

        var optMap = mapManager.getCollection().getMap(randomMapId);

        return optMap.map(CompletableFuture::completedFuture).orElseGet(() -> {
            var err = new NoSuchElementException("Map %s not found".formatted(randomMapId));
            return CompletableFuture.failedFuture(err);
        });

    }

    @NotNull
    private static String withSlash(Identifier gameId) {
        String path = gameId.getPath();

        if (path.endsWith("/")) {
            return path;
        }

        return path + "/";
    }
}
