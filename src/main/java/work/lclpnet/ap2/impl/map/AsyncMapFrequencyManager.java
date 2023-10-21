package work.lclpnet.ap2.impl.map;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.map.MapFrequencyManager;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public abstract class AsyncMapFrequencyManager implements MapFrequencyManager {

    private final Logger logger;
    private final Object2LongMap<Identifier> storage = new Object2LongOpenHashMap<>();

    public AsyncMapFrequencyManager(Logger logger) {
        this.logger = logger;
    }

    @Override
    public long getFrequency(Identifier mapId) {
        synchronized (this) {
            if (!storage.containsKey(mapId)) {
                logger.warn("No frequency stored for map {}. Consider preloading", mapId);
                return 0;
            }

            return storage.getLong(mapId);
        }
    }

    @Override
    public void setFrequency(Identifier mapId, long frequency) {
        setFrequencyInternal(mapId, frequency);

        dispatchUpdate(mapId);
    }

    protected void setFrequencyInternal(Identifier mapId, long frequency) {
        Objects.requireNonNull(mapId);

        synchronized (this) {
            storage.put(mapId, frequency);
        }
    }

    protected void dispatchUpdate(Identifier mapId) {
        CompletableFuture.runAsync(() -> write(mapId))
                .exceptionally(throwable -> {
                    logger.error("Failed to update map frequency for map {}", mapId);
                    return null;
                });
    }

    public CompletableFuture<Void> preload(Collection<Identifier> mapIds) {
        return CompletableFuture.runAsync(() -> read(mapIds));
    }

    protected abstract void read(Collection<Identifier> mapIds);

    protected abstract void write(Identifier mapId);
}
