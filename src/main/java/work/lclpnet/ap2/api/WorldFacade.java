package work.lclpnet.ap2.api;

import net.minecraft.util.Identifier;
import work.lclpnet.ap2.Prototype;

import java.util.concurrent.CompletableFuture;

@Prototype
public interface WorldFacade {

    /**
     * Changes the current map.
     * If the new map is not yet loaded, it will be loaded first.
     * All players will be moved to the new map.
     * Newly joining players will be moved to the new map as well.
     * @param identifier The map id.
     */
    CompletableFuture<Void> changeMap(Identifier identifier);

    /**
     * Marks the current map for removal.
     * When the map is next changed, the old map will be deleted.
     * If this method is invoked while a map not owned by this facade is active, this does nothing.
     */
    void deleteMap();
}
