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
    CompletableFuture<Void> changeMap(Identifier identifier, MapOptions options);


    default CompletableFuture<Void> changeMap(Identifier identifier) {
        return changeMap(identifier, MapOptions.TEMPORARY);
    }
}
