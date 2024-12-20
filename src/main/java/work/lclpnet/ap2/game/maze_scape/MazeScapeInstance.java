package work.lclpnet.ap2.game.maze_scape;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.game.maze_scape.debug.DebugFrustumCommand;
import work.lclpnet.ap2.game.maze_scape.debug.DebugPathCommand;
import work.lclpnet.ap2.game.maze_scape.setup.MSDebugController;
import work.lclpnet.ap2.game.maze_scape.setup.MSGenerator;
import work.lclpnet.ap2.game.maze_scape.setup.MSLoader;
import work.lclpnet.ap2.game.maze_scape.setup.OrientedStructurePiece;
import work.lclpnet.ap2.game.maze_scape.util.MSManager;
import work.lclpnet.ap2.game.maze_scape.util.MSStruct;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.util.math.MathUtil;
import work.lclpnet.kibu.cmd.type.CommandRegistrar;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.util.math.Matrix3i;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapUtils;

import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class MazeScapeInstance extends EliminationGameInstance implements MapBootstrap {

    private static final int
            MOB_SPAWN_DELAY_TICKS = Ticks.seconds(0),
            MOB_UPDATE_DELAY_TICKS = Ticks.seconds(1);

    private final MSDebugController debugController = new MSDebugController();
    private final Random random = new Random();
    private @Nullable MSStruct struct = null;
    private @Nullable MSManager manager = null;

    public MazeScapeInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    public CompletableFuture<Void> createWorldBootstrap(ServerWorld world, GameMap map) {
        world.setTimeOfDay(18_000);

        Logger logger = gameHandle.getLogger();
        var setup = new MSLoader(world, map, logger);

        return setup.load().thenCompose(res -> {
            long seed = new Random().nextLong();
            var random = new Random(seed);

            var generator = new MSGenerator(world, map, res, random, seed, logger, debugController);

            return generator.startGenerator().thenAccept(optGraph -> struct = optGraph.orElse(null));
        });
    }

    @Override
    protected void prepare() {
        if (struct == null) {
            gameHandle.getLogger().error("Failed to generate structure graph. Aborting the mini-game...");
            gameHandle.completeWithoutWinner();
            return;
        }

        CommandRegistrar commandRegistrar = gameHandle.getCommandRegistrar();

        new DebugPathCommand(struct, debugController).register(commandRegistrar);
        new DebugFrustumCommand(debugController).register(commandRegistrar);

        useSmoothDeath();
        useNoHealing();
        useRemainingPlayersDisplay();

        teleportPlayers();
    }

    @Override
    protected void ready() {
        manager = new MSManager(getWorld(), getMap(), struct, gameHandle.getParticipants(), random, gameHandle.getLogger());
        manager.init(gameHandle);

        TaskScheduler scheduler = gameHandle.getGameScheduler();
        scheduler.timeout(manager::spawnMobs, MOB_SPAWN_DELAY_TICKS);
        scheduler.interval(manager::updateMobs, MOB_UPDATE_DELAY_TICKS, MOB_SPAWN_DELAY_TICKS);
        scheduler.interval(manager::tick, 1);

        gameHandle.protect(config -> config.allow(ProtectionTypes.ALLOW_DAMAGE, this::allowDamage));
    }

    @Override
    protected void eliminate(ServerPlayerEntity player, @Nullable DamageSource source) {
        super.eliminate(player, source);

        if (source == null || manager == null) return;

        Entity attacker = source.getAttacker();

        if (attacker != null) {
            manager.onKillAcquired(attacker);
        }
    }

    private void teleportPlayers() {
        if (struct == null) return;

        OrientedStructurePiece oriented = struct.graph().root().oriented();

        if (oriented == null) return;

        Vec3d spawn = oriented.spawn();

        if (spawn == null) return;

        GameMap map = getMap();
        Matrix3i transformation = oriented.transformation();

        float yaw = MapUtils.getSpawnYaw(map);
        yaw = MathUtil.rotateYaw(yaw, transformation, new Vector3d());

        ServerWorld world = getWorld();

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            player.teleport(world, spawn.getX(), spawn.getY(), spawn.getZ(), yaw, 0);
        }
    }

    private boolean allowDamage(Entity entity, DamageSource source) {
        return !source.isOf(DamageTypes.PLAYER_ATTACK);
    }
}
