package work.lclpnet.ap2.impl.map;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.map.MapFrequencyManager;
import work.lclpnet.ap2.api.map.MapRandomizer;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapDescriptor;
import work.lclpnet.lobby.game.map.MapManager;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * A {@link MapRandomizer} that takes the amount of times the map was picked into account.
 * The goal of this randomizer is that every map should be played approximately equally often.
 */
public class BalancedMapRandomizer implements MapRandomizer {

    private final MapManager mapManager;
    private final MapFrequencyManager frequencyTracker;
    private final Random random;
    private Identifier forcedMap = null;

    public BalancedMapRandomizer(MapManager mapManager, MapFrequencyManager frequencyTracker, Random random) {
        this.mapManager = mapManager;
        this.frequencyTracker = frequencyTracker;
        this.random = random;
    }

    @Override
    public CompletableFuture<GameMap> nextMap(Identifier gameId) {
        var mapIds = getMapIds(gameId);

        if (frequencyTracker instanceof AsyncMapFrequencyManager async) {
            return async.preload(mapIds).thenCompose(ignored -> getRandomMap(mapIds));
        }

        return CompletableFuture.completedFuture(mapIds).thenCompose(this::getRandomMap);
    }

    @Override
    public CompletableFuture<Set<Identifier>> getAvailableMapIds(Identifier gameId) {
        Set<Identifier> mapIds = getMapIds(gameId);

        return CompletableFuture.completedFuture(mapIds);
    }

    @Override
    public void forceMap(@Nullable Identifier mapId) {
        forcedMap = mapId;
    }

    private Set<Identifier> getMapIds(Identifier gameId) {
        String prefix = withSlash(gameId);

        return mapManager.getCollection().getMaps().stream()
                .map(GameMap::getDescriptor)
                .map(MapDescriptor::getIdentifier)
                .filter(id -> id.getPath().startsWith(prefix))
                .collect(Collectors.toUnmodifiableSet());
    }

    private CompletableFuture<GameMap> getRandomMap(Collection<Identifier> mapIds) {
        return Optional.ofNullable(forcedMap)
                .flatMap(forced -> mapIds.stream()
                        .filter(forced::equals)
                        .findAny())
                .map(this::getMapById)
                .orElseGet(() -> getRandomLeastFrequentMap(mapIds));
    }

    private CompletableFuture<GameMap> getRandomLeastFrequentMap(Collection<Identifier> mapIds) {
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

        return getMapById(randomMapId);
    }

    private CompletableFuture<GameMap> getMapById(Identifier mapId) {
        var optMap = mapManager.getCollection().getMap(mapId);

        return optMap.map(CompletableFuture::completedFuture).orElseGet(() -> {
            var err = new NoSuchElementException("Map %s not found".formatted(mapId));
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
