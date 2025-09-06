package work.lclpnet.ap2.impl.map;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.map.MapRandomizer;
import work.lclpnet.ap2.impl.util.JsonFileQueuePersistence;
import work.lclpnet.ap2.impl.util.SeamlessQueue;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapManager;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.lang.Math.floor;
import static java.lang.Math.max;

public class SeamlessMapRandomizer implements MapRandomizer {

    private static final float MARGIN_PERCENT = 0.35f;

    private final MapManager mapManager;
    private final Random random;
    private final Logger logger;
    private @Nullable Identifier forcedMap = null;

    public SeamlessMapRandomizer(MapManager mapManager, Random random, Logger logger) {
        this.mapManager = mapManager;
        this.random = random;
        this.logger = logger;
    }

    @Override
    public CompletableFuture<GameMap> nextMap(Identifier gameId) {
        var mapIds = mapManager.getCollection()
                .mapIdsWithPrefix(gameId)
                .collect(Collectors.toSet());

        return Optional.ofNullable(forcedMap)
                .flatMap(forced -> mapIds.stream()
                        .filter(forced::equals)
                        .findAny())
                .map(this::getMapById)
                .or(() -> getRandomMap(gameId, mapIds))
                .orElseThrow(() -> new NoSuchElementException("No maps found for game: " + gameId));
    }

    private Optional<CompletableFuture<GameMap>> getRandomMap(Identifier gameId, Set<Identifier> mapIds) {
        if (mapIds.isEmpty()) {
            return Optional.empty();
        }

        int margin = max(0, (int) floor(mapIds.size() * MARGIN_PERCENT));

        return Optional.of(CompletableFuture.supplyAsync(() -> {
            Identifier queueId = gameId.withSuffixedPath("/map_queue");
            var queuePersistence = JsonFileQueuePersistence.create(queueId, Identifier.CODEC, logger);
            var transfer = queuePersistence.restore();

            var queue = new SeamlessQueue<>(mapIds, random, margin, transfer);

            Identifier next = queue.next();

            GameMap map = getMapById(next).join();
            queue.pushElement(next);
            queuePersistence.store(queue.transfer());

            return map;
        }));
    }

    @Override
    public void forceMap(@Nullable Identifier mapId) {
        this.forcedMap = mapId;
    }

    private CompletableFuture<GameMap> getMapById(Identifier mapId) {
        var optMap = mapManager.getCollection().getMap(mapId);

        return optMap.map(CompletableFuture::completedFuture).orElseGet(() -> {
            var err = new NoSuchElementException("Map %s not found".formatted(mapId));
            return CompletableFuture.failedFuture(err);
        });
    }
}
