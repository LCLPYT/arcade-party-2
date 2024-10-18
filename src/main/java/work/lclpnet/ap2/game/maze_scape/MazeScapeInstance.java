package work.lclpnet.ap2.game.maze_scape;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.game.maze_scape.setup.MSGenerator;
import work.lclpnet.ap2.game.maze_scape.setup.MSLoader;
import work.lclpnet.ap2.game.maze_scape.setup.OrientedStructurePiece;
import work.lclpnet.ap2.game.maze_scape.util.MSManager;
import work.lclpnet.ap2.game.maze_scape.util.MSStruct;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.util.math.MathUtil;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.util.math.Matrix3i;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapUtils;

import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class MazeScapeInstance extends EliminationGameInstance implements MapBootstrap {

    private static final int MOB_SPAWN_DELAY_TICKS = Ticks.seconds(0);
    private MSStruct struct;

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

            var generator = new MSGenerator(world, map, res, random, seed, logger);

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

        teleportPlayers();
    }

    private void teleportPlayers() {
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

    @Override
    protected void ready() {
        var manager = new MSManager(getWorld(), getMap(), struct, gameHandle.getParticipants(), gameHandle.getLogger());
        manager.init(gameHandle.getHookRegistrar());

        gameHandle.getGameScheduler().timeout(manager::spawnMobs, MOB_SPAWN_DELAY_TICKS);
    }
}
