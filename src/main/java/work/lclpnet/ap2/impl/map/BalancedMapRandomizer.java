package work.lclpnet.ap2.impl.map;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.map.MapFrequencyManager;
import work.lclpnet.ap2.api.map.MapRandomizer;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapCollection;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link MapRandomizer} that takes the amount of times the map was picked into account.
 * The goal of this randomizer is that every map should be played approximately equally often.
 */
public class BalancedMapRandomizer implements MapRandomizer {

    private final MapCollection mapManager;
    private final MapFrequencyManager frequencyTracker;
    private final Random random;

    public BalancedMapRandomizer(MapCollection mapManager, MapFrequencyManager frequencyTracker, Random random) {
        this.mapManager = mapManager;
        this.frequencyTracker = frequencyTracker;
        this.random = random;
    }

    @Override
    public CompletableFuture<GameMap> nextMap(Identifier gameId) {
        String prefix = withSlash(gameId);

        var mapIds = mapManager.getMaps().stream()
                .map(GameMap::getIdentifier)
                .filter(id -> id.getPath().startsWith(prefix))
                .distinct().toList();

        if (frequencyTracker instanceof AsyncMapFrequencyManager async) {
            return async.preload(mapIds).thenCompose(nil -> getRandomMap(mapIds));
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

        var optMap = mapManager.getMap(randomMapId);

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
