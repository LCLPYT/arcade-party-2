package work.lclpnet.ap2.game.pig_race;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.json.JSONArray;
import org.json.JSONObject;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.util.CollisionDetector;
import work.lclpnet.ap2.impl.game.DefaultGameInstance;
import work.lclpnet.ap2.impl.game.data.ScoreTimeDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.BlockBox;
import work.lclpnet.ap2.impl.util.checkpoint.Checkpoint;
import work.lclpnet.ap2.impl.util.checkpoint.CheckpointHelper;
import work.lclpnet.ap2.impl.util.checkpoint.CheckpointManager;
import work.lclpnet.ap2.impl.util.collision.ChunkedCollisionDetector;
import work.lclpnet.ap2.impl.util.collision.TickMovementObserver;
import work.lclpnet.ap2.impl.util.heads.PlayerHeadUtil;
import work.lclpnet.ap2.impl.util.heads.PlayerHeads;
import work.lclpnet.kibu.access.entity.PigEntityAccess;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.hook.ServerPlayConnectionHooks;
import work.lclpnet.kibu.hook.entity.EntityDismountCallback;
import work.lclpnet.kibu.hook.entity.EntityMountCallback;
import work.lclpnet.kibu.hook.player.PlayerTeleportedCallback;
import work.lclpnet.kibu.inv.item.ItemStackUtil;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapUtils;

import java.util.*;

public class PigRaceInstance extends DefaultGameInstance {

    private final ScoreTimeDataContainer<ServerPlayerEntity, PlayerRef> data = new ScoreTimeDataContainer<>(PlayerRef::create);
    private final Random random = new Random();
    private final CollisionDetector collisionDetector;
    private final TickMovementObserver movementObserver;
    private final Map<UUID, PendingPig> pendingPigs = new HashMap<>();
    private CheckpointManager checkpointManager;

    public PigRaceInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
        collisionDetector = new ChunkedCollisionDetector();
        movementObserver = new TickMovementObserver(collisionDetector, gameHandle.getParticipants()::isParticipating);
    }

    @Override
    protected DataContainer<ServerPlayerEntity, PlayerRef> getData() {
        return data;
    }

    @Override
    protected void prepare() {
        useTaskDisplay();

        HookRegistrar hooks = gameHandle.getHookRegistrar();

        // prevent dismounting
        hooks.registerHook(EntityDismountCallback.HOOK, (entity, vehicle) -> entity instanceof ServerPlayerEntity);

        // prevent mounting other entities while on another vehicle
        hooks.registerHook(EntityMountCallback.HOOK, (entity, vehicle) -> {
            Entity oldVehicle = entity.getVehicle();
            return entity instanceof ServerPlayerEntity && oldVehicle != null && oldVehicle.isAlive();
        });

        // mount a pig, after a player was teleported
        hooks.registerHook(PlayerTeleportedCallback.HOOK, player -> {
            PendingPig pig = pendingPigs.remove(player.getUuid());

            if (pig != null) {
                pig.create(player);
            }
        });

        // remove pigs when player quits
        hooks.registerHook(ServerPlayConnectionHooks.DISCONNECT, (handler, server) -> {
            Entity vehicle = handler.player.getVehicle();

            if (vehicle != null) {
                vehicle.discard();
            }
        });

        BlockBox spawnBounds = MapUtil.readBox(getMap().requireProperty("spawn-bounds"));
        JSONObject goalJson = getMap().requireProperty("goal");
        Checkpoint goal = Checkpoint.fromJson(goalJson);

        teleportPlayers(spawnBounds);
        setupCheckpoints(spawnBounds, goal);

        movementObserver.init(gameHandle.getGameScheduler(), hooks, gameHandle.getServer());
    }

    @Override
    protected void ready() {
        openGate();

        HookRegistrar hooks = gameHandle.getHookRegistrar();
        Participants participants = gameHandle.getParticipants();

        CheckpointHelper.setupResetItem(hooks, winManager::isGameOver, participants::isParticipating)
                .then(this::resetPlayerToCheckpoint);

        // reset players who have fallen into the lava / water
        gameHandle.getGameScheduler().interval(() -> {
            for (ServerPlayerEntity player : participants) {
                Entity vehicle = player.getVehicle();
                if (vehicle == null) continue;

                if (vehicle.isSubmergedInWater()) {
                    resetPlayerToCheckpoint(player);
                }

                BlockPos pos = vehicle.getBlockPos();
                BlockState state = player.getServerWorld().getBlockState(pos);

                if (state.isOf(Blocks.LAVA)) {
                    resetPlayerToCheckpoint(player);
                }
            }
        }, 1);

        participants.forEach(this::giveResetItem);
    }

    private void resetPlayerToCheckpoint(ServerPlayerEntity player) {
        Checkpoint checkpoint = checkpointManager.getCheckpoint(player);

        if (player.getVehicle() instanceof PigEntity pig) {
            pig.discard();
        }

        BlockPos pos = checkpoint.pos();
        double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
        float yaw = checkpoint.yaw();

        pendingPigs.put(player.getUuid(), new PendingPig(x, y, z, yaw));
        player.teleport(getWorld(), x, y, z, yaw, 0f);

        player.setFireTicks(0);
    }

    private void setupCheckpoints(BlockBox spawnBounds, Checkpoint goal) {
        GameMap map = getMap();
        JSONArray array = map.requireProperty("checkpoints");
        List<Checkpoint> checkpoints = new ArrayList<>(array.length());

        // add the spawn as initial checkpoint
        Vec3d spawn = MapUtils.getSpawnPosition(map);
        BlockPos spawnPos = new BlockPos((int) Math.floor(spawn.getX()), (int) Math.floor(spawn.getY()), (int) Math.floor(spawn.getZ()));
        float spawnYaw = MapUtils.getSpawnYaw(map);

        checkpoints.add(new Checkpoint(spawnPos, spawnYaw, spawnBounds));

        for (Object obj : array) {
            if (!(obj instanceof JSONObject json)) continue;

            Checkpoint checkpoint = Checkpoint.fromJson(json);
            checkpoints.add(checkpoint);
        }

        // add the goal as last checkpoint
        checkpoints.add(goal);

        checkpointManager = new CheckpointManager(checkpoints);
        checkpointManager.init(collisionDetector, movementObserver);

        CheckpointHelper.notifyWhenReached(checkpointManager, gameHandle.getTranslations());

        checkpointManager.whenCheckpointReached(this::onReachedCheckpoint);
    }

    private void onReachedCheckpoint(ServerPlayerEntity player, int checkpoint) {
        data.setScore(player, checkpoint);

        if (checkpoint >= checkpointManager.getCheckpoints().size() - 1) {
            winManager.win(player);
        }
    }

    private void openGate() {
        GameMap map = getMap();
        ServerWorld world = getWorld();

        BlockBox bounds = MapUtil.readBox(map.requireProperty("gate"));

        BlockState air = Blocks.AIR.getDefaultState();

        for (BlockPos pos : bounds) {
            world.setBlockState(pos, air);
        }
    }

    private void teleportPlayers(BlockBox bounds) {
        GameMap map = getMap();
        ServerWorld world = getWorld();

        float yaw = 0;

        if (map.hasProperty("spawn-yaw")) {
            yaw = MapUtil.readAngle(map.requireProperty("spawn-yaw"));
        }

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            Vec3d pos = bounds.getRandomPos(random);

            double x = pos.getX(), y = pos.getY(), z = pos.getZ();

            pendingPigs.put(player.getUuid(), new PendingPig(x, y, z, yaw));
            player.teleport(world, x, y, z, yaw, 0f);

            giveStick(player);
        }
    }

    private void giveStick(ServerPlayerEntity player) {
        TranslationService translations = gameHandle.getTranslations();

        ItemStack stick = new ItemStack(Items.CARROT_ON_A_STICK);
        ItemStackUtil.setUnbreakable(stick, true);
        stick.setCustomName(translations.translateText(player, "game.ap2.pig_race.boost")
                .styled(style -> style.withItalic(false).withFormatting(Formatting.GOLD)));

        player.getInventory().setStack(4, stick);
    }

    private void giveResetItem(ServerPlayerEntity player) {
        TranslationService translations = gameHandle.getTranslations();

        ItemStack reset = PlayerHeadUtil.getItem(PlayerHeads.REDSTONE_BLOCK_REFRESH);
        reset.setCustomName(translations.translateText(player, "game.ap2.reset").formatted(Formatting.RED)
                .styled(style -> style.withItalic(false)));

        player.getInventory().setStack(8, reset);

        PlayerInventoryAccess.setSelectedSlot(player, 4);
    }

    private record PendingPig(double x, double y, double z, float yaw) {

        public void create(ServerPlayerEntity player) {
            ServerWorld world = player.getServerWorld();

            PigEntity pig = new PigEntity(EntityType.PIG, world);
            pig.setInvulnerable(true);
            pig.setBodyYaw(yaw);
            pig.setPos(x, y + 0.1, z);
            PigEntityAccess.setSaddled(pig, true);

            world.spawnEntity(pig);

            player.startRiding(pig, true);
        }
    }
}
