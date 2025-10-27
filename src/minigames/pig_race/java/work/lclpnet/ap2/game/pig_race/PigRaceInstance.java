package work.lclpnet.ap2.game.pig_race;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.StriderEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.api.music.WeightedSong;
import work.lclpnet.ap2.api.util.heads.PlayerHead;
import work.lclpnet.ap2.game.pig_race.util.PRProgress;
import work.lclpnet.ap2.game.pig_race.util.PRScoreboard;
import work.lclpnet.ap2.game.pig_race.util.SegmentedPath;
import work.lclpnet.ap2.impl.game.FFAGameInstance;
import work.lclpnet.ap2.impl.game.data.CombinedDataContainer;
import work.lclpnet.ap2.impl.game.data.DoubleScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.OrderedDataContainer;
import work.lclpnet.ap2.impl.game.data.Ordering;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.map.schema.SchemaHolder;
import work.lclpnet.ap2.impl.music.MusicHelper;
import work.lclpnet.ap2.impl.util.ApRegistries;
import work.lclpnet.ap2.impl.util.Fireworks;
import work.lclpnet.ap2.impl.util.ParticleHelper;
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedPlayerBossBar;
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
import work.lclpnet.gaco.math.SplinePath;
import work.lclpnet.kibu.access.entity.PlayerInventoryAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.ServerPlayConnectionHooks;
import work.lclpnet.kibu.hook.entity.EntityDismountCallback;
import work.lclpnet.kibu.hook.entity.EntityMountCallback;
import work.lclpnet.kibu.hook.player.PlayerInventoryHooks;
import work.lclpnet.kibu.hook.player.PlayerTeleportedCallback;
import work.lclpnet.kibu.hook.util.PositionRotation;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.game.map.GameMap;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static net.minecraft.util.Formatting.*;
import static work.lclpnet.ap2.impl.music.MusicHelper.ARCADE_PARTY_GAME_TAG;
import static work.lclpnet.ap2.impl.util.ItemHelper.unbreakable;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class PigRaceInstance extends FFAGameInstance implements MapBootstrap {

    public static final String NEXT_ROUND_SONG_ID = "ap_begin";
    public static final int STICK_SLOT = 4;
    private static final double CATCHUP_MIN_DISTANCE = 10.0;
    private static final double CATCHUP_MAX_DISTANCE = 75.0;
    private static final double MAX_CATCHUP_BOOST = 0.4;

    private final OrderedDataContainer<ServerPlayerEntity, PlayerRef> winnerData = new OrderedDataContainer<>(PlayerRef::create);
    private final DoubleScoreDataContainer<ServerPlayerEntity, PlayerRef> distanceData = new DoubleScoreDataContainer<>(PlayerRef::create, Ordering.ASCENDING, "ap2.score.blocks_away");
    private final CombinedDataContainer<ServerPlayerEntity, PlayerRef> combinedData = new CombinedDataContainer<>(List.of(winnerData, distanceData));

    private final Random random = new Random();
    private final CollisionDetector collisionDetector;
    private final TickMovementObserver movementObserver;
    private final Map<UUID, PendingEntity<?>> pendingEntities = new HashMap<>();
    private final SchemaHolder<PigRaceSchema> schemaHolder;

    private CheckpointManager checkpointManager;
    private PRProgress progress;
    private PRScoreboard scoreboard;
    private @Nullable WeightedSong nextRoundSong = null;
    private Variant variant = Variant.PIG;
    private double speed = 1.0;

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
    public @NotNull CompletableFuture<Void> createWorldBootstrap(@NotNull ServerWorld world, @NotNull GameMap map) {
        return gameHandle.getSongManager().getSongAndCache(ARCADE_PARTY_GAME_TAG, NEXT_ROUND_SONG_ID)
                .thenAccept(song -> nextRoundSong = song.orElse(null));
    }

    @Override
    protected void prepare() {
        this.variant = getVariant();

        JSONObject properties = getMap().getProperties();
        int rounds = properties.optInt("rounds", 1);

        speed = properties.optNumber("speed", 0.0).doubleValue();

        Translations translations = gameHandle.getTranslations();
        Participants participants = gameHandle.getParticipants();

        Team team = createTeam();
        var visibilityManager = new VisibilityManager(team, Visibility.PARTIALLY_VISIBLE);
        var visibility = new VisibilityHandler(visibilityManager, translations, participants);

        visibility.init(gameHandle.getHooks());

        initHooks(team, visibilityManager);

        PigRaceSchema schema = schemaHolder.get();

        BlockBox spawnBounds = schema.getSpawnBounds();
        Checkpoint goal = schema.getGoal();

        teleportPlayers(spawnBounds);
        setupCheckpoints(spawnBounds, goal);

        movementObserver.init(gameHandle.getGameScheduler(), gameHandle.getHooks(), gameHandle.getServer());

        visibility.giveItems(0);

        List<Checkpoint> progressMarkers = new ArrayList<>(schema.getProgressMarkers());
        var segmentedPath = SegmentedPath.create(augmentPath(schema, rounds), progressMarkers, gameHandle.getLogger());

        segmentedPath.init(gameHandle.getParticipants(), gameHandle.getGameScheduler(), gameHandle.getHooks(),
                gameHandle.getServer(), commons().debugController());

        var bossBar = createBossBar(rounds);

        progress = new PRProgress(gameHandle, segmentedPath, rounds);
        scoreboard = new PRScoreboard(gameHandle, progress, bossBar);

        scoreboard.setup();
    }

    private @NotNull DynamicTranslatedPlayerBossBar createBossBar(int rounds) {
        if (rounds > 1) {
            return usePlayerDynamicDisplay("game.ap2.pig_race.task_rounds", styled(1, YELLOW), styled(rounds, YELLOW));
        }

        return usePlayerDynamicTaskDisplay();
    }

    private void initHooks(Team team, VisibilityManager visibilityManager) {
        HookRegistrar hooks = gameHandle.getHooks();
        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();

        // prevent dismounting
        hooks.registerHook(EntityDismountCallback.HOOK, (entity, vehicle) -> entity instanceof ServerPlayerEntity);

        // prevent mounting other entities while on another vehicle
        hooks.registerHook(EntityMountCallback.HOOK, (entity, vehicle, force) -> {
            Entity oldVehicle = entity.getVehicle();
            return entity instanceof ServerPlayerEntity && oldVehicle != null && oldVehicle.isAlive();
        });

        // mount a new entity, after a player was teleported
        hooks.registerHook(PlayerTeleportedCallback.HOOK, player -> {
            var pending = pendingEntities.remove(player.getUuid());

            if (pending == null) return;

            var entity = pending.create(player);

            EntityAttributeInstance instance = entity.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);

            if (instance != null) {
                instance.addPersistentModifier(new EntityAttributeModifier(gameHandle.getGameInfo().identifier("map_boost"), speed, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }

            scoreboardManager.joinTeam(entity, team);
            visibilityManager.updateVisibilityOf(entity);
        });

        // remove entity when player quits
        hooks.registerHook(ServerPlayConnectionHooks.DISCONNECT, (handler, server) -> {
            Entity vehicle = handler.player.getVehicle();

            if (vehicle != null) {
                vehicle.discard();
            }
        });
    }

    private Variant getVariant() {
        String variant = getMap().getProperties().optString("variant", Variant.PIG.name().toLowerCase(Locale.ROOT));

        try {
            return Variant.valueOf(variant.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            gameHandle.getLogger().error("Invalid variant \"{}\"", variant);
            return Variant.PIG;
        }
    }

    private SplinePath augmentPath(PigRaceSchema schema, int rounds) {
        SplinePath path = schema.getPath();

        if (rounds <= 1) {
            return path;
        }

        var keypoints = new ArrayList<>(path.getKeypoints());

        keypoints.addLast(keypoints.getFirst());

        return SplinePath.create(keypoints, gameHandle.getLogger()).orElseThrow();
    }

    @Override
    protected void ready() {
        openGate();

        HookRegistrar hooks = gameHandle.getHooks();
        Participants participants = gameHandle.getParticipants();

        CheckpointHelper.setupResetItem(hooks, winManager::isGameOver, participants::isParticipating)
                .then(this::resetPlayerToCheckpoint);

        // reset players who have fallen into the lava / water
        gameHandle.getGameScheduler().interval(this::tick, 1);

        if (checkpointManager.getCheckpoints().size() > 2) {
            participants.forEach(this::giveResetItem);
        }

        hooks.registerHook(PlayerInventoryHooks.SWAP_HANDS, (player, slot) -> {
            resetPlayerToCheckpoint(player);
            return true;
        });

        scoreboard.addScoreboardRanking();

        progress.update();
        scoreboard.updateRanking();

        gameHandle.getGameScheduler().interval(() -> {
            progress.update();
            scoreboard.updateRanking();
        }, 1);

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            PlayerInventoryAccess.setSelectedSlot(player, STICK_SLOT);
        }
    }

    private void tick() {
        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            if (!(player.getVehicle() instanceof LivingEntity vehicle)) continue;

            player.setFireTicks(0);

            Box box = vehicle.getType().getDimensions().getBoxAt(vehicle.getPos());
            World world = vehicle.getWorld();

            for (BlockPos pos : BlockPos.iterate(box)) {
                BlockState state = world.getBlockState(pos);

                if (state.isIn(BlockTags.FIRE)
                        || variant != Variant.STRIDER && state.isOf(Blocks.LAVA)
                        || variant == Variant.STRIDER && state.isOf(Blocks.WATER)) {
                    resetPlayerToCheckpoint(player);
                    break;
                }
            }

            if (vehicle.isSubmergedInWater()) {
                resetPlayerToCheckpoint(player);
            }

            updateCatchupSpeed(player, vehicle);
        }
    }

    private void updateCatchupSpeed(ServerPlayerEntity player, LivingEntity vehicle) {
        double maxDist = progress.getFurthestAbsoluteDistance();
        double playerDist = progress.getAbsoluteDistance(player);

        double dist = max(0, maxDist - playerDist);

        double len = CATCHUP_MAX_DISTANCE - CATCHUP_MIN_DISTANCE;
        double scale = max(0, min(1, ((dist - CATCHUP_MIN_DISTANCE) / len)));
        double boost = MAX_CATCHUP_BOOST * scale;

        EntityAttributeInstance instance = vehicle.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);

        if (instance == null) return;

        Identifier id = gameHandle.getGameInfo().identifier("catchup");

        EntityAttributeModifier modifier = new EntityAttributeModifier(id, boost, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);

        if (instance.hasModifier(id)) {
            instance.updateModifier(modifier);
        } else {
            instance.addTemporaryModifier(modifier);
        }
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

        Entity vehicle = player.getVehicle();

        if (isVehicle(vehicle)) {
            vehicle.discard();
        }

        Vec3d pos = checkpoint.pos();
        double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
        float yaw = checkpoint.yaw();

        pendingEntities.put(player.getUuid(), createPending(x, y, z, yaw));
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

        movementObserver.whenEntering(goal.bounds(), this::onEnterGoal);
    }

    private synchronized void onEnterGoal(ServerPlayerEntity player) {
        if (winManager.isGameOver() || !progress.getPath().isInLastSegment(player)) return;

        int round = progress.getRound(player);

        if (round < progress.getRounds()) {
            nextRound(player, round);
            return;
        }

        Fireworks.spawnGoalFirework(player);

        winnerData.add(player);

        for (ServerPlayerEntity other : gameHandle.getParticipants()) {
            if (other == player) continue;

            double remaining = progress.getAbsoluteRemaining(other);

            distanceData.setScore(other, remaining);
        }

        winManager.complete();
    }

    private void nextRound(ServerPlayerEntity player, int round) {
        progress.incrementRound(player);
        checkpointManager.resetCheckpoints(player);
        scoreboard.updateRoundDisplay(player);

        if (nextRoundSong != null) {
            MusicHelper.playSong(nextRoundSong, 0.5f, player, gameHandle.getServer(), gameHandle.getSharedSongCache(), gameHandle.getLogger());
        }

        var text = gameHandle.getTranslations().translateText("game.ap2.pig_race.round_title", Text.literal("#" + (round + 1)).formatted(YELLOW))
                .formatted(AQUA)
                .translateFor(player);

        Title.get(player).title(Text.empty(), text, 10, 30, 10);

        ParticleHelper.spawnParticleFor(ParticleTypes.FIREWORK, player.getX(), player.getY(), player.getZ(),
                100, 1, 1, 1, 0.5, List.of(player));
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
            BlockPos pos = bounds.randomBlockPos(random);

            double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;

            pendingEntities.put(player.getUuid(), createPending(x, y, z, yaw));
            player.teleport(world, x, y, z, Set.of(), yaw, 0f, true);

            giveStick(player);
        }
    }

    private PendingEntity<?> createPending(double x, double y, double z, float yaw) {
        Function<ServerWorld, ? extends LivingEntity> factory = switch (variant) {
            case PIG -> world -> new PigEntity(EntityType.PIG, world);
            case STRIDER -> world -> new StriderEntity(EntityType.STRIDER, world);
        };

        return new PendingEntity<>(x, y, z, yaw, factory);
    }

    private void giveStick(ServerPlayerEntity player) {
        Translations translations = gameHandle.getTranslations();

        ItemStack stick = unbreakable(new ItemStack(switch (variant) {
            case PIG -> Items.CARROT_ON_A_STICK;
            case STRIDER -> Items.WARPED_FUNGUS_ON_A_STICK;
        }));

        stick.set(DataComponentTypes.CUSTOM_NAME, translations.translateText(player, "game.ap2.pig_race.boost")
                .styled(style -> style.withItalic(false).withFormatting(GOLD)));

        player.getInventory().setStack(STICK_SLOT, stick);

        PlayerInventoryAccess.setSelectedSlot(player, STICK_SLOT);
    }

    private void giveResetItem(ServerPlayerEntity player) {
        Translations translations = gameHandle.getTranslations();

        PlayerHead head = getWorld().getRegistryManager()
                .getOrThrow(ApRegistries.PLAYER_HEAD)
                .getOptionalValue(PlayerHeads.REDSTONE_BLOCK_REFRESH)
                .orElseThrow();

        ItemStack reset = head.createStack();

        reset.set(DataComponentTypes.CUSTOM_NAME, translations.translateText(player, "ap2.game.reset").formatted(RED)
                .styled(style -> style.withItalic(false)));

        player.getInventory().setStack(8, reset);

        PlayerInventoryAccess.setSelectedSlot(player, 4);
    }

    private boolean isVehicle(Entity vehicle) {
        return vehicle instanceof PigEntity || vehicle instanceof StriderEntity;
    }

    private record PendingEntity<T extends LivingEntity>(double x, double y, double z, float yaw, Function<ServerWorld, T> factory) {

        public T create(ServerPlayerEntity player) {
            ServerWorld world = player.getWorld();

            T entity = factory.apply(world);
            entity.setInvulnerable(true);
            entity.setBodyYaw(yaw);
            entity.setPos(x, y + 0.1, z);
            entity.equipStack(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));

            world.spawnEntity(entity);

            player.startRiding(entity, true);

            return entity;
        }
    }

    private enum Variant { PIG, STRIDER }
}
