package work.lclpnet.ap2.game.mimicry;

import net.minecraft.server.world.ServerWorld;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.game.mimicry.data.MimicryManager;
import work.lclpnet.ap2.game.mimicry.data.MimicryRoom;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.util.world.StackedRoomGenerator;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.concurrent.CompletableFuture;

public class MimicryInstance extends EliminationGameInstance implements MapBootstrap {

    private MimicryManager manager = null;

    public MimicryInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    public CompletableFuture<Void> createWorldBootstrap(ServerWorld world, GameMap map) {
        var generator = new StackedRoomGenerator<>(world, map, StackedRoomGenerator.Coordinates.ABSOLUTE, MimicryRoom::new);

        return generator.generate(gameHandle.getParticipants())
                .thenAccept(mapping -> manager = new MimicryManager(gameHandle, mapping))
                .exceptionally(throwable -> {
                    gameHandle.getLogger().error("Failed to create rooms", throwable);
                    return null;
                });
    }

    @Override
    protected void prepare() {
        ServerWorld world = getWorld();

        manager.eachParticipant((player, room) -> room.teleport(player, world));
    }

    @Override
    protected void ready() {

    }
}
