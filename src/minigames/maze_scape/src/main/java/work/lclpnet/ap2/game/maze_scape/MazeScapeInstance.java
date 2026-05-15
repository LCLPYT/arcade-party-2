package work.lclpnet.ap2.game.maze_scape;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.slf4j.Logger;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.MiniGameResults;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.api.util.model.ModelManager;
import work.lclpnet.ap2.ext.mc.LevelExtensionsKt;
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
import work.lclpnet.game.impl.prot.ProtectionTypes;
import work.lclpnet.game.map.GameMap;
import work.lclpnet.game.map.MapUtils;
import work.lclpnet.kibu.cmd.type.CommandRegistrar;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.util.math.Matrix3i;

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
    public @NotNull CompletableFuture<Void> createWorldBootstrap(@NotNull ServerLevel world, @NotNull GameMap map) {
        LevelExtensionsKt.setDayTime(world, 18_000);

        ModelManager modelManager = ApResources.getInstance();

        debugController = new MSDebugController(commons(map, world).debugController());

        if (ApConstants.DEBUG) {
            debugController.init(modelManager);
        }

        Logger logger = gameHandle.getLogger();
        var setup = new MSLoader(world, map, logger);

        return setup.load().thenCompose(res -> {
            if (MSLoader.DEBUG_PIECES) {
                struct = null;
                return CompletableFuture.completedFuture(null);
            }

            long seed = new Random().nextLong();
            var random = new Random(seed);

            var generator = new MSGenerator(world, map, res, random, seed, logger, debugController);

            return generator.startGenerator().thenAccept(optGraph -> struct = optGraph.orElse(null));
        });
    }

    @Override
    protected void prepare() {
        if (MSLoader.DEBUG_PIECES) {
            for (ServerPlayer player : gameHandle.getParticipants()) {
                Abilities abilities = player.getAbilities();
                abilities.mayfly = true;
                abilities.flying = true;
                player.onUpdateAbilities();
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false, false));
            }
            return;
        }

        if (struct == null) {
            gameHandle.getLogger().error("Failed to generate structure graph. Aborting the mini-game...");
            gameHandle.complete(MiniGameResults.EMPTY);
            return;
        }

        CommandRegistrar commandRegistrar = gameHandle.getCommands();

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
    protected void go() {
        if (MSLoader.DEBUG_PIECES) return;

        ServerLevel world = getWorld();
        Participants participants = gameHandle.getParticipants();

        manager = new MSManager(world, getMap(), struct, participants, random,
                gameHandle.getLogger(), debugController);

        manager.init(gameHandle);

        TaskScheduler scheduler = gameHandle.getScheduler();
        scheduler.interval(manager::updateMobs, MOB_UPDATE_DELAY_TICKS, MOB_SPAWN_DELAY_TICKS);
        scheduler.interval(manager::tick, 1);
        scheduler.interval(this::checkPits, 1);

        scheduler.timeout(() -> {
            manager.spawnMobs();

            var reveal = new MonsterReveal(ApResources.getInstance(), manager.participants(), world, manager.monsters());
            reveal.start(scheduler, gameHandle.getHooks());

            scheduler.timeout(reveal::stop, MOB_REVEAL_TICKS);
        }, MOB_SPAWN_DELAY_TICKS);

        gameHandle.protect(config -> ProtectionTypes.ALLOW_DAMAGE.allow(config, this::allowDamage));
    }

    @Override
    public void eliminate(ServerPlayer player, @Nullable DamageSource source) {
        super.eliminate(player, source);

        if (source == null || manager == null) return;

        Entity attacker = source.getEntity();

        if (attacker != null) {
            manager.onKillAcquired(attacker);
        }
    }

    private void teleportPlayers() {
        if (struct == null) return;

        OrientedStructurePiece oriented = struct.graph().root().oriented();

        if (oriented == null) return;

        Vec3 spawn = oriented.spawn();

        if (spawn == null) return;

        GameMap map = getMap();
        Matrix3i transformation = oriented.transformation();

        float yaw = MapUtils.getSpawnYaw(map);
        yaw = MathUtil.rotateYaw(yaw, transformation, new Vector3d());

        ServerLevel world = getWorld();

        for (ServerPlayer player : gameHandle.getParticipants()) {
            player.teleportTo(world, spawn.x(), spawn.y(), spawn.z(), Set.of(), yaw, 0, true);
        }
    }

    private boolean allowDamage(Entity entity, DamageSource source) {
        return !source.is(DamageTypes.PLAYER_ATTACK);
    }

    private void checkPits() {
        gameHandle.getParticipants().forEach(this::checkInPit);
    }

    private void checkInPit(ServerPlayer player) {
        if (struct == null) return;

        var node = struct.nodeAt(player.position());

        if (node == null) return;

        OrientedStructurePiece oriented = node.oriented();

        if (oriented == null) return;

        AABB box = player.getBoundingBox();

        // check if bottom face of bounding box is completely within a pit
        double eps = 1e-6;

        int minX = Mth.floor(box.minX - eps);
        int minZ = Mth.floor(box.minZ - eps);
        int maxX = Mth.floor(box.maxX + eps);
        int maxZ = Mth.floor(box.maxZ + eps);
        int by = Mth.floor(box.minY);

        if (!BlockPos.betweenClosedStream(minX, by, minZ, maxX, by, maxZ).allMatch(oriented::isPitAt)) return;

        // check if on ground
        double delta = 0.1;
        int minY = Mth.floor(box.minY - delta);
        int maxY = Mth.floor(box.minY + delta);

        ServerLevel world = getWorld();
        CollisionContext context = CollisionContext.of(player);
        AABB collisionBox = box.setMinY(box.minY - delta).setMaxY(box.minY + delta);
        VoxelShape boxShape = Shapes.create(collisionBox);

        if (BlockPos.betweenClosedStream(minX, minY, minZ, maxX, maxY, maxZ)
                .filter(pos -> !oriented.isPitAt(pos))  // blocks marked as pit are considered air
                .noneMatch(pos -> collides(pos, world, context, collisionBox, boxShape))) return;

        // hit the ground within a pit
        DeathMessages msg = gameHandle.getDeathMessages();

        eliminate(player, msg.root(FELL_INTO_PIT, msg.wrap(player)));
    }

    private static boolean collides(BlockPos pos, ServerLevel world, CollisionContext context, AABB collisionBox, VoxelShape boxShape) {
        BlockState state = world.getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(world, pos, context);

        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        if (shape == Shapes.block()) {
            return collisionBox.intersects(x, y, z, x + 1.0, y + 1.0, z + 1.0);
        }

        VoxelShape offset = shape.move(x, y, z);

        return !offset.isEmpty() && Shapes.joinIsNotEmpty(offset, boxShape, BooleanOp.AND);
    }
}
