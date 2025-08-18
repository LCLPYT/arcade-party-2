package work.lclpnet.ap2.game.maze_scape;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.slf4j.Logger;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.MiniGameResults;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.api.util.model.ModelManager;
import work.lclpnet.ap2.game.maze_scape.debug.DebugFrustumCommand;
import work.lclpnet.ap2.game.maze_scape.debug.DebugPathCommand;
import work.lclpnet.ap2.game.maze_scape.setup.MSDebugController;
import work.lclpnet.ap2.game.maze_scape.setup.MSGenerator;
import work.lclpnet.ap2.game.maze_scape.setup.MSLoader;
import work.lclpnet.ap2.game.maze_scape.setup.OrientedStructurePiece;
import work.lclpnet.ap2.game.maze_scape.util.MSManager;
import work.lclpnet.ap2.game.maze_scape.util.MSStruct;
import work.lclpnet.ap2.game.maze_scape.util.MonsterReveal;
import work.lclpnet.ap2.impl.game.EliminationGameInstance;
import work.lclpnet.ap2.impl.resource.ApResources;
import work.lclpnet.ap2.impl.util.DeathMessages;
import work.lclpnet.ap2.impl.util.math.MathUtil;
import work.lclpnet.ap2.impl.util.world.ChunkPersistence;
import work.lclpnet.kibu.cmd.type.CommandRegistrar;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.util.math.Matrix3i;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapUtils;

import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class MazeScapeInstance extends EliminationGameInstance implements MapBootstrap {

    private static final int
            MOB_SPAWN_DELAY_TICKS = Ticks.seconds(0),
            MOB_UPDATE_DELAY_TICKS = Ticks.seconds(1),
            MOB_REVEAL_TICKS = Ticks.seconds(8);

    private static final String FELL_INTO_PIT = "game.ap2.maze_scape.fell_into_pit";

    private final Random random = new Random();
    private MSDebugController debugController;
    private @Nullable MSStruct struct = null;
    private @Nullable MSManager manager = null;

    public MazeScapeInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    public CompletableFuture<Void> createWorldBootstrap(ServerWorld world, GameMap map) {
        world.setTimeOfDay(18_000);

        ModelManager modelManager = ApResources.getInstance();

        debugController = new MSDebugController(commons(map, world).debugController());

        if (ApConstants.DEBUG) {
            debugController.init(modelManager);
        }

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
            gameHandle.complete(MiniGameResults.EMPTY);
            return;
        }

        CommandRegistrar commandRegistrar = gameHandle.getCommandRegistrar();

        if (ApConstants.DEBUG) {
            new DebugPathCommand(struct, debugController).register(commandRegistrar);
            new DebugFrustumCommand(debugController).register(commandRegistrar);
        }

        useSmoothDeath();
        useNoHealing();
        useRemainingPlayersDisplay();

        var persistence = new ChunkPersistence(getWorld(), gameHandle);
        int mapChunkRadius = MSGenerator.getMaxChunkSize(getMap());

        persistence.markQuadPersistent(-mapChunkRadius, -mapChunkRadius, mapChunkRadius, mapChunkRadius);

        commons().displayHealth();
        teleportPlayers();
    }

    @Override
    protected void ready() {
        ServerWorld world = getWorld();
        Participants participants = gameHandle.getParticipants();

        manager = new MSManager(world, getMap(), struct, participants, random,
                gameHandle.getLogger(), debugController);

        manager.init(gameHandle);

        TaskScheduler scheduler = gameHandle.getGameScheduler();
        scheduler.interval(manager::updateMobs, MOB_UPDATE_DELAY_TICKS, MOB_SPAWN_DELAY_TICKS);
        scheduler.interval(manager::tick, 1);
        scheduler.interval(this::checkPits, 1);

        scheduler.timeout(() -> {
            manager.spawnMobs();

            var reveal = new MonsterReveal(ApResources.getInstance(), manager.participants(), world, manager.monsters());
            reveal.start(scheduler, gameHandle.getHookRegistrar());

            scheduler.timeout(reveal::stop, MOB_REVEAL_TICKS);
        }, MOB_SPAWN_DELAY_TICKS);

        gameHandle.protect(config -> config.allow(ProtectionTypes.ALLOW_DAMAGE, this::allowDamage));
    }

    @Override
    public void eliminate(ServerPlayerEntity player, @Nullable DamageSource source) {
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
            player.teleport(world, spawn.getX(), spawn.getY(), spawn.getZ(), Set.of(), yaw, 0, true);
        }
    }

    private boolean allowDamage(Entity entity, DamageSource source) {
        return !source.isOf(DamageTypes.PLAYER_ATTACK);
    }

    private void checkPits() {
        gameHandle.getParticipants().forEach(this::checkInPit);
    }

    private void checkInPit(ServerPlayerEntity player) {
        if (struct == null) return;

        var node = struct.nodeAt(player.getPos());

        if (node == null) return;

        OrientedStructurePiece oriented = node.oriented();

        if (oriented == null) return;

        Box box = player.getBoundingBox();

        // check if bottom face of bounding box is completely within a pit
        double eps = 1e-6;

        int minX = MathHelper.floor(box.minX - eps);
        int minZ = MathHelper.floor(box.minZ - eps);
        int maxX = MathHelper.floor(box.maxX + eps);
        int maxZ = MathHelper.floor(box.maxZ + eps);
        int by = MathHelper.floor(box.minY);

        if (!BlockPos.stream(minX, by, minZ, maxX, by, maxZ).allMatch(oriented::isPitAt)) return;

        // check if on ground
        double delta = 0.1;
        int minY = MathHelper.floor(box.minY - delta);
        int maxY = MathHelper.floor(box.minY + delta);

        ServerWorld world = getWorld();
        ShapeContext context = ShapeContext.of(player);
        Box collisionBox = box.withMinY(box.minY - delta).withMaxY(box.minY + delta);
        VoxelShape boxShape = VoxelShapes.cuboid(collisionBox);

        if (BlockPos.stream(minX, minY, minZ, maxX, maxY, maxZ)
                .filter(pos -> !oriented.isPitAt(pos))  // blocks marked as pit are considered air
                .noneMatch(pos -> collides(pos, world, context, collisionBox, boxShape))) return;

        // hit the ground within a pit
        DeathMessages msg = gameHandle.getDeathMessages();

        eliminate(player, msg.root(FELL_INTO_PIT, msg.wrap(player)));
    }

    private static boolean collides(BlockPos pos, ServerWorld world, ShapeContext context, Box collisionBox, VoxelShape boxShape) {
        BlockState state = world.getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(world, pos, context);

        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        if (shape == VoxelShapes.fullCube()) {
            return collisionBox.intersects(x, y, z, x + 1.0, y + 1.0, z + 1.0);
        }

        VoxelShape offset = shape.offset(x, y, z);

        return !offset.isEmpty() && VoxelShapes.matchesAnywhere(offset, boxShape, BooleanBiFunction.AND);
    }
}
