package work.lclpnet.ap2.api.map;

import net.minecraft.util.Identifier;
import work.lclpnet.ap2.api.game.MapReady;

import java.util.concurrent.CompletableFuture;

public interface MapFacade {

    /**
     * Opens a random map that matches a given game identifier.
     * For example, if <code>ap:spleef</code> is given, this method will open a random map for the "spleef" mini-game.
     * @param gameId The game identifier.
     * @return A future that completes if the map was opened.
     */
    CompletableFuture<Void> openRandomMap(Identifier gameId);

    void openRandomMap(Identifier gameId, MapReady onReady);
}
