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
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.game.MiniGameHandle;
import work.lclpnet.ap2.game.MiniGameResults;
import work.lclpnet.ap2.game.base.EliminationGameInstance;
import work.lclpnet.ap2.game.maze_scape.debug.DebugFrustumCommand;
import work.lclpnet.ap2.game.maze_scape.debug.DebugPathCommand;
import work.lclpnet.ap2.game.maze_scape.setup.MSDebugController;
import work.lclpnet.ap2.game.maze_scape.setup.MSGenerator;
import work.lclpnet.ap2.game.maze_scape.setup.MSLoader;
import work.lclpnet.ap2.game.maze_scape.setup.OrientedStructurePiece;
import work.lclpnet.ap2.game.maze_scape.util.MSManager;
import work.lclpnet.ap2.game.maze_scape.util.MSStruct;
import work.lclpnet.ap2.game.maze_scape.util.MonsterReveal;
import work.lclpnet.ap2.game.player.Participants;
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
import work.lclpnet.kibu.translate.text.TranslatedText;
import work.lclpnet.kibu.util.math.Matrix3i;

import java.util.Random;
import java.util.Set;

public class MazeScapeInstance extends EliminationGameInstance {

    private static final int
            MOB_SPAWN_DELAY_TICKS = Ticks.seconds(0),
            MOB_UPDATE_DELAY_TICKS = Ticks.seconds(1),
            MOB_REVEAL_TICKS = Ticks.seconds(8);

    private static final String FELL_INTO_PIT = "fell_into_pit";

    private final Random random = new Random();
    private final @Nullable MSStruct struct;
    private final MSDebugController debugController;
    private @Nullable MSManager manager = null;

    public MazeScapeInstance(MiniGameHandle gameHandle, ServerLevel world, GameMap map, @Nullable MSStruct struct, MSDebugController debugController) {
        super(gameHandle, world, map);
        this.struct = struct;
        this.debugController = debugController;
    }

    @Override
    protected void prepare() {
        if (MSLoader.DEBUG_PIECES) {
            for (ServerPlayer player : getGameHandle().getParticipants()) {
                Abilities abilities = player.getAbilities();
                abilities.mayfly = true;
                abilities.flying = true;
                player.onUpdateAbilities();
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false, false));
            }
            return;
        }

        if (struct == null) {
            getGameHandle().getLogger().error("Failed to generate structure graph. Aborting the mini-game...");
            getGameHandle().complete(MiniGameResults.EMPTY);
            return;
        }

        CommandRegistrar commandRegistrar = getGameHandle().getCommands();

        if (ApConstants.DEBUG) {
            new DebugPathCommand(struct, debugController).register(commandRegistrar);
            new DebugFrustumCommand(debugController).register(commandRegistrar);
        }

        useSmoothDeath();
        useNoHealing();
        useRemainingPlayersDisplay();

        var persistence = new ChunkPersistence(getLevel(), getGameHandle());
        int mapChunkRadius = MSGenerator.getMaxChunkSize(getMap());

        persistence.markQuadPersistent(-mapChunkRadius, -mapChunkRadius, mapChunkRadius, mapChunkRadius);

        commons().displayHealth();
    }

    @Override
    protected void go() {
        if (MSLoader.DEBUG_PIECES) return;

        ServerLevel world = getLevel();
        Participants participants = getGameHandle().getParticipants();

        manager = new MSManager(world, getMap(), struct, participants, random,
                getGameHandle().getLogger(), debugController);

        manager.init(getGameHandle());

        TaskScheduler scheduler = getGameHandle().getScheduler();
        scheduler.interval(manager::updateMobs, MOB_UPDATE_DELAY_TICKS, MOB_SPAWN_DELAY_TICKS);
        scheduler.interval(manager::tick, 1);
        scheduler.interval(this::checkPits, 1);

        scheduler.timeout(() -> {
            manager.spawnMobs();

            var reveal = new MonsterReveal(ApResources.getInstance(), manager.participants(), world, manager.monsters());
            reveal.start(scheduler, getGameHandle().getHooks());

            scheduler.timeout(reveal::stop, MOB_REVEAL_TICKS);
        }, MOB_SPAWN_DELAY_TICKS);

        getGameHandle().protect(config -> ProtectionTypes.ALLOW_DAMAGE.allow(config, this::allowDamage));
    }

    @Override
    public void eliminate(@NotNull ServerPlayer player, @Nullable DamageSource source, @Nullable TranslatedText customMsg) {
        super.eliminate(player, source, customMsg);

        if (source == null || manager == null) return;

        Entity attacker = source.getEntity();

        if (attacker != null) {
            manager.onKillAcquired(attacker);
        }
    }

    @Override
    protected void teleportPlayers() {
        if (struct == null) return;

        OrientedStructurePiece oriented = struct.graph().root().oriented();

        if (oriented == null) return;

        Vec3 spawn = oriented.spawn();

        if (spawn == null) return;

        GameMap map = getMap();
        Matrix3i transformation = oriented.transformation();

        float yaw = MapUtils.getSpawnYaw(map);
        yaw = MathUtil.rotateYaw(yaw, transformation, new Vector3d());

        ServerLevel world = getLevel();

        for (ServerPlayer player : getGameHandle().getParticipants()) {
            player.teleportTo(world, spawn.x(), spawn.y(), spawn.z(), Set.of(), yaw, 0, true);
        }
    }

    private boolean allowDamage(Entity entity, DamageSource source) {
        return !source.is(DamageTypes.PLAYER_ATTACK);
    }

    private void checkPits() {
        getGameHandle().getParticipants().forEach(this::checkInPit);
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

        ServerLevel world = getLevel();
        CollisionContext context = CollisionContext.of(player);
        AABB collisionBox = box.setMinY(box.minY - delta).setMaxY(box.minY + delta);
        VoxelShape boxShape = Shapes.create(collisionBox);

        if (BlockPos.betweenClosedStream(minX, minY, minZ, maxX, maxY, maxZ)
                .filter(pos -> !oriented.isPitAt(pos))  // blocks marked as pit are considered air
                .noneMatch(pos -> collides(pos, world, context, collisionBox, boxShape))) return;

        // hit the ground within a pit
        DeathMessages msg = getGameHandle().getDeathMessages();

        eliminate(player, null, msg.root(FELL_INTO_PIT, msg.wrap(player)));
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
