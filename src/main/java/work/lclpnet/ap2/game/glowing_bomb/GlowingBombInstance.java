package work.lclpnet.ap2.game.glowing_bomb;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.api.map.MapBootstrapFunction;
import work.lclpnet.ap2.game.glowing_bomb.data.GbManager;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.map.ServerThreadMapBootstrap;
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.Random;

public class GlowingBombInstance extends EliminationGameInstance implements MapBootstrapFunction {

    private final Random random = new Random();
    private final SimpleMovementBlocker movementBlocker;
    private GbManager manager;

    public GlowingBombInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        disableTeleportEliminated();

        movementBlocker = new SimpleMovementBlocker(gameHandle.getGameScheduler());
        movementBlocker.setModifySpeedAttribute(false);
    }

    @Override
    protected MapBootstrap getMapBootstrap() {
        return new ServerThreadMapBootstrap(this);
    }

    @Override
    public void bootstrapWorld(ServerWorld world, GameMap map) {
        manager = new GbManager(world, map, random);
        manager.setupAnchors(gameHandle.getParticipants());
    }

    @Override
    protected void prepare() {
        HookRegistrar hooks = gameHandle.getHookRegistrar();
        Participants participants = gameHandle.getParticipants();

        movementBlocker.init(hooks);

        manager.teleportPlayers(participants);

        for (ServerPlayerEntity player : participants) {
            movementBlocker.disableMovement(player);
        }
    }

    @Override
    protected void ready() {

    }

    @Override
    protected void onEliminated(ServerPlayerEntity player) {
        movementBlocker.enableMovement(player);
    }
}
