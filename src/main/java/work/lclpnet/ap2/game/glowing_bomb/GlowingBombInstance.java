package work.lclpnet.ap2.game.glowing_bomb;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.api.map.MapBootstrapFunction;
import work.lclpnet.ap2.game.glowing_bomb.data.GbBomb;
import work.lclpnet.ap2.game.glowing_bomb.data.GbManager;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.map.ServerThreadMapBootstrap;
import work.lclpnet.ap2.impl.scene.Scene;
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
        manager = new GbManager(world, map, random, gameHandle.getParticipants());
        manager.setupAnchors();
    }

    @Override
    protected void prepare() {
        HookRegistrar hooks = gameHandle.getHookRegistrar();

        movementBlocker.init(hooks);

        manager.teleportPlayers();

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            movementBlocker.disableMovement(player);
        }
    }

    @Override
    protected void ready() {
        spawnBomb();
    }

    @Override
    protected void onEliminated(ServerPlayerEntity player) {
        movementBlocker.enableMovement(player);
    }

    private void spawnBomb() {
        Vec3d pos = manager.randomBombLocation();

        if (pos == null) {
            winManager.checkForWinner(gameHandle.getParticipants().stream(), resolver);
            return;
        }

        GbBomb bomb = new GbBomb();
        bomb.position.set(pos.getX(), pos.getY(), pos.getZ());
        bomb.scale.set(0.4);

        Scene scene = new Scene(getWorld());
        scene.add(bomb);
        scene.spawnObjects();
    }
}
