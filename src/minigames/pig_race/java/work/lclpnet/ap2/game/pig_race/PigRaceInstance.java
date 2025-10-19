package work.lclpnet.ap2.game.pig_race;

import lombok.Synchronized;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.util.heads.PlayerHead;
import work.lclpnet.ap2.impl.game.FFAGameInstance;
import work.lclpnet.ap2.impl.game.data.CombinedDataContainer;
import work.lclpnet.ap2.impl.game.data.DoubleScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.OrderedDataContainer;
import work.lclpnet.ap2.impl.game.data.Ordering;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.map.schema.SchemaHolder;
import work.lclpnet.ap2.impl.util.ApRegistries;
import work.lclpnet.ap2.impl.util.checkpoint.CheckpointHelper;
import work.lclpnet.ap2.impl.util.checkpoint.CheckpointManager;
import work.lclpnet.ap2.impl.util.handler.Visibility;
import work.lclpnet.ap2.impl.util.handler.VisibilityHandler;
import work.lclpnet.ap2.impl.util.handler.VisibilityManager;
import work.lclpnet.ap2.impl.util.heads.PlayerHeads;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.gaco.collisions.ChunkedCollisionDetector;
import work.lclpnet.gaco.collisions.CollisionDetector;
import work.lclpnet.gaco.collisions.movement.TickMovementObserver;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.gaco.ds.Checkpoint;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.ServerPlayConnectionHooks;
import work.lclpnet.kibu.hook.entity.EntityDismountCallback;
import work.lclpnet.kibu.hook.entity.EntityMountCallback;
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks;
import work.lclpnet.kibu.hook.player.PlayerTeleportedCallback;
import work.lclpnet.kibu.hook.util.PositionRotation;
import work.lclpnet.kibu.translate.Translations;

import java.util.*;

import static work.lclpnet.ap2.impl.util.ItemHelper.unbreakable;

public class PigRaceInstance extends FFAGameInstance {

    private final OrderedDataContainer<ServerPlayerEntity, PlayerRef> winnerData = new OrderedDataContainer<>(PlayerRef::create);
    private final DoubleScoreDataContainer<ServerPlayerEntity, PlayerRef> distanceData = new DoubleScoreDataContainer<>(PlayerRef::create, Ordering.ASCENDING, "ap2.score.blocks_away");
    private final CombinedDataContainer<ServerPlayerEntity, PlayerRef> combinedData = new CombinedDataContainer<>(List.of(winnerData, distanceData));
    private final Random random = new Random();
    private final CollisionDetector collisionDetector;
    private final TickMovementObserver movementObserver;
    private final Map<UUID, PendingPig> pendingPigs = new HashMap<>();
    private final SchemaHolder<PigRaceSchema> schemaHolder;
    private CheckpointManager checkpointManager;
    private SegmentedPath path;

    public PigRaceInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        collisionDetector = new ChunkedCollisionDetector();
        movementObserver = new TickMovementObserver(collisionDetector, gameHandle.getParticipants()::isParticipating);

        schemaHolder = useSchema(PigRaceSchema.class);
    }

    @Override
    protected DataContainer<ServerPlayerEntity, PlayerRef> getData() {
        return combinedData;
    }

    @Override
    protected void prepare() {
        PigRaceSchema schema = schemaHolder.get();
        useTaskDisplay();

        HookRegistrar hooks = gameHandle.getHooks();
        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();
        Translations translations = gameHandle.getTranslations();
        Participants participants = gameHandle.getParticipants();

        Team team = createTeam();
        var visibilityManager = new VisibilityManager(team, Visibility.PARTIALLY_VISIBLE);
        var visibility = new VisibilityHandler(visibilityManager, translations, participants);

        visibility.init(hooks);

        // prevent dismounting
        hooks.registerHook(EntityDismountCallback.HOOK, (entity, vehicle) -> entity instanceof ServerPlayerEntity);

        // prevent mounting other entities while on another vehicle
        hooks.registerHook(EntityMountCallback.HOOK, (entity, vehicle, force) -> {
            Entity oldVehicle = entity.getVehicle();
            return entity instanceof ServerPlayerEntity && oldVehicle != null && oldVehicle.isAlive();
        });

        // mount a pig, after a player was teleported
        hooks.registerHook(PlayerTeleportedCallback.HOOK, player -> {
            PendingPig pendingPig = pendingPigs.remove(player.getUuid());

            if (pendingPig == null) return;

            PigEntity pig = pendingPig.create(player);
            scoreboardManager.joinTeam(pig, team);
            visibilityManager.updateVisibilityOf(pig);
        });

        // remove pigs when player quits
        hooks.registerHook(ServerPlayConnectionHooks.DISCONNECT, (handler, server) -> {
            Entity vehicle = handler.player.getVehicle();

            if (vehicle != null) {
                vehicle.discard();
            }
        });

        BlockBox spawnBounds = schema.getSpawnBounds();
        Checkpoint goal = schema.getGoal();

        teleportPlayers(spawnBounds);
        setupCheckpoints(spawnBounds, goal);

        movementObserver.init(gameHandle.getGameScheduler(), hooks, gameHandle.getServer());

        visibility.giveItems(0);

        List<Checkpoint> progressMarkers = new ArrayList<>(schema.getProgressMarkers());
        var segmentedPath = SegmentedPath.create(schema.getPath(), progressMarkers, gameHandle.getLogger());

        segmentedPath.init(gameHandle.getParticipants(), gameHandle.getGameScheduler(), gameHandle.getHooks(),
                gameHandle.getServer(), commons().debugController());

        path = segmentedPath;
    }

    @Override
    protected void ready() {
        openGate();

        HookRegistrar hooks = gameHandle.getHooks();
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
                BlockState state = player.getWorld().getBlockState(pos);

                if (state.isOf(Blocks.LAVA)) {
                    resetPlayerToCheckpoint(player);
                }
            }
        }, 1);

        participants.forEach(this::giveResetItem);

        hooks.registerHook(PlayerInventoryHooks.SWAP_HANDS, (player, slot) -> {
            resetPlayerToCheckpoint(player);
            return true;
        });
    }

    private Team createTeam() {
        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();
        Team team = scoreboardManager.createTeam("team");
        team.setCollisionRule(AbstractTeam.CollisionRule.NEVER);
        scoreboardManager.joinTeam(gameHandle.getParticipants(), team);

        return team;
    }

    private void resetPlayerToCheckpoint(ServerPlayerEntity player) {
        Checkpoint checkpoint = checkpointManager.getCheckpoint(player);

        if (player.getVehicle() instanceof PigEntity pig) {
            pig.discard();
        }

        Vec3d pos = checkpoint.pos();
        double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
        float yaw = checkpoint.yaw();

        pendingPigs.put(player.getUuid(), new PendingPig(x, y, z, yaw));
        player.teleport(getWorld(), x, y, z, Set.of(), yaw, checkpoint.pitch(), true);

        player.setFireTicks(0);
    }

    private void setupCheckpoints(BlockBox spawnBounds, Checkpoint goal) {
        var schema = schemaHolder.get();

        List<Checkpoint> checkpoints = new ArrayList<>(schema.getCheckpoints());

        PositionRotation spawn = schema.getSpawn();
        checkpoints.addFirst(new Checkpoint(new Vec3d(spawn.getX(), spawn.getY(), spawn.getZ()), spawn.getYaw(), spawn.getPitch(), spawnBounds));

        checkpoints.addLast(goal);

        checkpointManager = new CheckpointManager(checkpoints, commons().debugController());
        checkpointManager.init(collisionDetector, movementObserver, getWorld());

        CheckpointHelper.notifyWhenReached(checkpointManager, gameHandle.getTranslations());

        checkpointManager.whenCheckpointReached(this::onReachedCheckpoint);
    }

    @Synchronized
    private void onReachedCheckpoint(ServerPlayerEntity player, int checkpoint) {
        if (checkpoint < checkpointManager.getCheckpoints().size() - 1) return;

        winnerData.add(player);

        for (ServerPlayerEntity other : gameHandle.getParticipants()) {
            if (other == player) continue;

            double progress = path.getProgress(other);
            double remaining = (1 - progress) * path.getCombinedLength();

            distanceData.setScore(other, remaining);
        }

        winManager.complete();
    }

    private void openGate() {
        ServerWorld world = getWorld();

        BlockState air = Blocks.AIR.getDefaultState();

        for (BlockBox bounds : schemaHolder.get().getGates()) {
            for (BlockPos pos : bounds) {
                world.setBlockState(pos, air);
            }
        }
    }

    private void teleportPlayers(BlockBox bounds) {
        ServerWorld world = getWorld();

        var schema = schemaHolder.get();
        PositionRotation spawn = schema.getSpawn();
        float yaw = spawn.getYaw();

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            Vec3d pos = bounds.randomPos(random);

            double x = pos.getX(), y = pos.getY(), z = pos.getZ();

            pendingPigs.put(player.getUuid(), new PendingPig(x, y, z, yaw));
            player.teleport(world, x, y, z, Set.of(), yaw, 0f, true);

            giveStick(player);
        }
    }

    private void giveStick(ServerPlayerEntity player) {
        Translations translations = gameHandle.getTranslations();

        ItemStack stick = unbreakable(new ItemStack(Items.CARROT_ON_A_STICK));

        stick.set(DataComponentTypes.CUSTOM_NAME, translations.translateText(player, "game.ap2.pig_race.boost")
                .styled(style -> style.withItalic(false).withFormatting(Formatting.GOLD)));

        player.getInventory().setStack(4, stick);
    }

    private void giveResetItem(ServerPlayerEntity player) {
        Translations translations = gameHandle.getTranslations();

        PlayerHead head = getWorld().getRegistryManager()
                .getOrThrow(ApRegistries.PLAYER_HEAD)
                .getOptionalValue(PlayerHeads.REDSTONE_BLOCK_REFRESH)
                .orElseThrow();

        ItemStack reset = head.createStack();

        reset.set(DataComponentTypes.CUSTOM_NAME, translations.translateText(player, "ap2.game.reset").formatted(Formatting.RED)
                .styled(style -> style.withItalic(false)));

        player.getInventory().setStack(8, reset);

        PlayerInventoryAccess.setSelectedSlot(player, 4);
    }

    private record PendingPig(double x, double y, double z, float yaw) {

        public PigEntity create(ServerPlayerEntity player) {
            ServerWorld world = player.getWorld();

            PigEntity pig = new PigEntity(EntityType.PIG, world);
            pig.setInvulnerable(true);
            pig.setBodyYaw(yaw);
            pig.setPos(x, y + 0.1, z);
            pig.equipStack(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));

            world.spawnEntity(pig);

            player.startRiding(pig, true);

            return pig;
        }
    }
}
