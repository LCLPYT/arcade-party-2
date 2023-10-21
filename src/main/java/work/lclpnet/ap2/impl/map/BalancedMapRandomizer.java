package work.lclpnet.ap2.impl.map;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import work.lclpnet.ap2.api.map.MapFrequencyManager;
import work.lclpnet.ap2.api.map.MapRandomizer;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapCollection;

import java.util.Optional;
import java.util.Random;

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
    public Optional<GameMap> nextMap(Identifier gameId) {
        String prefix = withSlash(gameId);

        var mapFrequencies = mapManager.getMaps().stream()
                .map(GameMap::getIdentifier)
                .filter(id -> id.getPath().startsWith(prefix))
                .distinct()
                .map(id -> Pair.of(id, frequencyTracker.getFrequency(id)))
                .toList();

        if (mapFrequencies.isEmpty()) {
            return Optional.empty();
        }

        long minFrequency = mapFrequencies.stream()
                .mapToLong(Pair::right)
                .min()
                .orElseThrow();

        var leastPlayed = mapFrequencies.stream()
                .filter(pair -> pair.right() == minFrequency)
                .map(Pair::left)
                .toList();

        Identifier randomMapId = leastPlayed.get(random.nextInt(leastPlayed.size()));

        return mapManager.getMap(randomMapId);
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
