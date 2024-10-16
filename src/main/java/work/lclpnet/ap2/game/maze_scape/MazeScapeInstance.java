package work.lclpnet.ap2.game.maze_scape;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.game.maze_scape.gen.Graph;
import work.lclpnet.ap2.game.maze_scape.setup.*;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.util.math.MathUtil;
import work.lclpnet.kibu.util.math.Matrix3i;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapUtils;

import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;

public class MazeScapeInstance extends EliminationGameInstance implements MapBootstrap {

    private Graph<Connector3, StructurePiece, OrientedStructurePiece> graph;

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

            return generator.startGenerator().thenAccept(optGraph -> graph = optGraph.orElse(null));
        });
    }

    @Override
    protected void prepare() {
        if (graph == null) {
            gameHandle.getLogger().error("Failed to generate structure graph. Aborting the mini-game...");
            gameHandle.completeWithoutWinner();
            return;
        }

        teleportPlayers();
    }

    private void teleportPlayers() {
        OrientedStructurePiece oriented = graph.root().oriented();

        if (oriented == null) return;

        StructurePiece piece = oriented.piece();
        Vec3d baseSpawn = piece.spawn();

        if (baseSpawn == null) return;

        BlockPos pos = oriented.pos();
        Matrix3i transformation = oriented.transformation();
        Vec3d spawn = transformation.transform(baseSpawn).add(pos.getX(), pos.getY(), pos.getZ());

        GameMap map = getMap();

        float yaw = MapUtils.getSpawnYaw(map);
        yaw = MathUtil.rotateYaw(yaw, transformation, new Vector3d());

        ServerWorld world = getWorld();

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            player.teleport(world, spawn.getX(), spawn.getY(), spawn.getZ(), yaw, 0);
        }
    }

    @Override
    protected void ready() {

    }
}
