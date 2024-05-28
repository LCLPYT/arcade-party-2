package work.lclpnet.ap2.game.glowing_bomb;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
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
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.Random;

public class GlowingBombInstance extends EliminationGameInstance implements MapBootstrapFunction {

    private static final int MIN_BOMB_GLOW_STONE = 1;
    private static final int MAX_BOMB_GLOW_STONE = 3;
    private static final int MIN_BOMB_FUSE_TICKS = Ticks.seconds(5);
    private static final int MAX_BOMB_FUSE_TICKS = Ticks.seconds(14);
    private final Random random = new Random();
    private final SimpleMovementBlocker movementBlocker;
    private GbManager manager = null;
    private Scene scene = null;
    private GbBomb bomb = null;

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
//            movementBlocker.disableMovement(player);
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

        bomb = new GbBomb();
        bomb.scale.set(0.4);
        bomb.position.set(pos.getX(), pos.getY(), pos.getZ());

        int amount = MIN_BOMB_GLOW_STONE + random.nextInt(Math.max(1, MAX_BOMB_GLOW_STONE - MIN_BOMB_GLOW_STONE + 1));
//        bomb.setGlowStoneAmount(amount, random);

        scene = new Scene(getWorld());
        scene.add(bomb);
        scene.spawnObjects();

        TaskScheduler scheduler = gameHandle.getGameScheduler();
        scene.animate(1, scheduler);

//        int fuseTicks = MIN_BOMB_FUSE_TICKS + random.nextInt(MAX_BOMB_FUSE_TICKS - MIN_BOMB_FUSE_TICKS + 1);
//        scheduler.timeout(this::explodeBomb, fuseTicks);
    }

    private void explodeBomb() {
        scene.stopAnimation();  // TODO start explode animation

        ServerWorld world = getWorld();
        Vector3d pos = bomb.getWorldPosition();
        double x = pos.x(), y = pos.y(), z = pos.z();

        world.spawnParticles(ParticleTypes.EXPLOSION, x, y, z, 200, 1, 1, 1, 0.5);
        world.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 0.9f, 1.2f);
    }
}
