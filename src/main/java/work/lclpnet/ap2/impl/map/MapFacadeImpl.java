package work.lclpnet.ap2.impl.map;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameRules;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.game.MapReady;
import work.lclpnet.ap2.api.map.MapFacade;
import work.lclpnet.ap2.api.map.MapRandomizer;
import work.lclpnet.lobby.game.api.WorldFacade;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.concurrent.CompletableFuture;

public class MapFacadeImpl implements MapFacade {

    private final WorldFacade worldFacade;
    private final MapRandomizer mapRandomizer;
    private final MinecraftServer server;
    private final Logger logger;

    public MapFacadeImpl(WorldFacade worldFacade, MapRandomizer mapRandomizer, MinecraftServer server, Logger logger) {
        this.worldFacade = worldFacade;
        this.mapRandomizer = mapRandomizer;
        this.server = server;
        this.logger = logger;
    }

    @Override
    public CompletableFuture<Pair<ServerWorld, GameMap>> openRandomMap(Identifier gameId) {
        return mapRandomizer.nextMap(gameId)
                .thenCompose(map -> {
                    Identifier id = map.getDescriptor().getIdentifier();
                    return worldFacade.changeMap(id).thenApply(world -> Pair.of(world, map));
                })
                .thenApply(pair -> {
                    setupWorld(pair.left());
                    return pair;
                });
    }

    @Override
    public void openRandomMap(Identifier gameId, MapReady callback) {
        openRandomMap(gameId)
                .thenCompose(pair -> server.submit(() -> callback.onReady(pair.left(), pair.right())))
                .exceptionally(throwable -> {
                    logger.error("Failed to open a random map for game {}", gameId, throwable);
                    return null;
                });
    }

    private void setupWorld(ServerWorld world) {
        world.getGameRules().get(GameRules.DO_IMMEDIATE_RESPAWN).set(true, server);
    }
}
