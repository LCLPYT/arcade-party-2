package work.lclpnet.ap2.game.pig_race;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
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

import static java.lang.Math.clamp;
import static java.lang.Math.max;
import static net.minecraft.ChatFormatting.*;
import static work.lclpnet.ap2.impl.music.MusicHelper.ARCADE_PARTY_GAME_TAG;
import static work.lclpnet.ap2.impl.util.ItemHelper.unbreakable;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class PigRaceInstance extends FFAGameInstance implements MapBootstrap {

    public static final String NEXT_ROUND_SONG_ID = "ap_begin";
    public static final int STICK_SLOT = 4;
    private static final double CATCHUP_MIN_DISTANCE = 10.0;
    private static final double CATCHUP_MAX_DISTANCE = 75.0;
    private static final double MAX_CATCHUP_BOOST = 0.4;

    private final OrderedDataContainer<ServerPlayer, PlayerRef> winnerData = new OrderedDataContainer<>(PlayerRef::create);
    private final DoubleScoreDataContainer<ServerPlayer, PlayerRef> distanceData = new DoubleScoreDataContainer<>(PlayerRef::create, Ordering.ASCENDING, "ap2.score.blocks_away");
    private final CombinedDataContainer<ServerPlayer, PlayerRef> combinedData = new CombinedDataContainer<>(List.of(winnerData, distanceData));

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
    protected DataContainer<ServerPlayer, PlayerRef> getData() {
        return combinedData;
    }

    @Override
    public @NotNull CompletableFuture<Void> createWorldBootstrap(@NotNull ServerLevel world, @NotNull GameMap map) {
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

        PlayerTeam team = createTeam();
        var visibilityManager = new VisibilityManager(team, Visibility.PARTIALLY_VISIBLE);
        var visibility = new VisibilityHandler(visibilityManager, translations, participants);

        visibility.init(gameHandle.getHooks());

        initHooks(team, visibilityManager);

        PigRaceSchema schema = schemaHolder.get();

        BlockBox spawnBounds = schema.getSpawnBounds();
        Checkpoint goal = schema.getGoal();

        teleportPlayers(spawnBounds);
        setupCheckpoints(spawnBounds, goal);

        movementObserver.init(gameHandle.getScheduler(), gameHandle.getHooks(), gameHandle.getServer());

        visibility.giveItems(0);

        List<Checkpoint> progressMarkers = new ArrayList<>(schema.getProgressMarkers());
        var segmentedPath = SegmentedPath.create(augmentPath(schema, rounds), progressMarkers, gameHandle.getLogger());

        segmentedPath.init(gameHandle.getParticipants(), gameHandle.getScheduler(), gameHandle.getHooks(),
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

    private void initHooks(PlayerTeam team, VisibilityManager visibilityManager) {
        HookRegistrar hooks = gameHandle.getHooks();
        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();

        // prevent dismounting
        EntityDismountCallback.HOOK.registerWith(hooks, (entity, _) -> entity instanceof ServerPlayer);

        // prevent mounting other entities while on another vehicle
        EntityMountCallback.HOOK.registerWith(hooks, (entity, _, _) -> {
            Entity oldVehicle = entity.getVehicle();
            return entity instanceof ServerPlayer && oldVehicle != null && oldVehicle.isAlive();
        });

        // mount a new entity, after a player was teleported
        PlayerTeleportedCallback.HOOK.registerWith(hooks, player -> {
            var pending = pendingEntities.remove(player.getUUID());

            if (pending == null) return;

            var entity = pending.create(player);

            AttributeInstance instance = entity.getAttribute(Attributes.MOVEMENT_SPEED);

            if (instance != null) {
                instance.addPermanentModifier(new AttributeModifier(gameHandle.getGameInfo().identifier("map_boost"), speed, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }

            scoreboardManager.joinTeam(entity, team);
            visibilityManager.updateVisibilityOf(entity);
        });

        // remove entity when player quits
        ServerPlayConnectionHooks.DISCONNECT.registerWith(hooks, (handler, _) -> {
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
        } catch (IllegalArgumentException _) {
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
    protected void go() {
        openGate();

        HookRegistrar hooks = gameHandle.getHooks();
        Participants participants = gameHandle.getParticipants();

        CheckpointHelper.setupResetItem(hooks, winManager::isGameOver, participants::isParticipating)
                .then(this::resetPlayerToCheckpoint);

        // reset players who have fallen into the lava / water
        gameHandle.getScheduler().interval(this::tick, 1);

        if (checkpointManager.getCheckpoints().size() > 2) {
            participants.forEach(this::giveResetItem);
        }

        PlayerInventoryHooks.SWAP_HANDS.registerWith(hooks, (player, _) -> {
            resetPlayerToCheckpoint(player);
            return true;
        });

        scoreboard.addScoreboardRanking();

        progress.update();
        scoreboard.updateRanking();

        gameHandle.getScheduler().interval(() -> {
            progress.update();
            scoreboard.updateRanking();
        }, 1);

        for (ServerPlayer player : gameHandle.getParticipants()) {
            PlayerInventoryAccess.setSelectedSlot(player, STICK_SLOT);
        }
    }

    private void tick() {
        for (ServerPlayer player : gameHandle.getParticipants()) {
            if (!(player.getVehicle() instanceof LivingEntity vehicle)) continue;

            player.setRemainingFireTicks(0);

            AABB box = vehicle.getType().getDimensions().makeBoundingBox(vehicle.position());
            Level world = vehicle.level();

            for (BlockPos pos : BlockPos.betweenClosed(box)) {
                BlockState state = world.getBlockState(pos);

                if (state.is(BlockTags.FIRE)
                        || variant != Variant.STRIDER && state.is(Blocks.LAVA)
                        || variant == Variant.STRIDER && state.is(Blocks.WATER)) {
                    resetPlayerToCheckpoint(player);
                    break;
                }
            }

            if (vehicle.isUnderWater()) {
                resetPlayerToCheckpoint(player);
            }

            updateCatchupSpeed(player, vehicle);
        }
    }

    private void updateCatchupSpeed(ServerPlayer player, LivingEntity vehicle) {
        double maxDist = progress.getFurthestAbsoluteDistance();
        double playerDist = progress.getAbsoluteDistance(player);

        double dist = max(0, maxDist - playerDist);

        double len = CATCHUP_MAX_DISTANCE - CATCHUP_MIN_DISTANCE;
        double scale = clamp((dist - CATCHUP_MIN_DISTANCE) / len, 0, 1);
        double boost = MAX_CATCHUP_BOOST * scale;

        AttributeInstance instance = vehicle.getAttribute(Attributes.MOVEMENT_SPEED);

        if (instance == null) return;

        Identifier id = gameHandle.getGameInfo().identifier("catchup");

        AttributeModifier modifier = new AttributeModifier(id, boost, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

        if (instance.hasModifier(id)) {
            instance.addOrUpdateTransientModifier(modifier);
        } else {
            instance.addTransientModifier(modifier);
        }
    }

    private PlayerTeam createTeam() {
        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();
        PlayerTeam team = scoreboardManager.createTeam("team");
        team.setCollisionRule(Team.CollisionRule.NEVER);
        scoreboardManager.joinTeam(gameHandle.getParticipants(), team);

        return team;
    }

    private void resetPlayerToCheckpoint(ServerPlayer player) {
        Checkpoint checkpoint = checkpointManager.getCheckpoint(player);

        Entity vehicle = player.getVehicle();

        if (isVehicle(vehicle)) {
            vehicle.discard();
        }

        Vec3 pos = checkpoint.pos();
        double x = pos.x() + 0.5, y = pos.y(), z = pos.z() + 0.5;
        float yaw = checkpoint.yaw();

        pendingEntities.put(player.getUUID(), createPending(x, y, z, yaw));
        player.teleportTo(getWorld(), x, y, z, Set.of(), yaw, checkpoint.pitch(), true);

        player.setRemainingFireTicks(0);
    }

    private void setupCheckpoints(BlockBox spawnBounds, Checkpoint goal) {
        var schema = schemaHolder.get();

        List<Checkpoint> checkpoints = new ArrayList<>(schema.getCheckpoints());

        PositionRotation spawn = schema.getSpawn();
        checkpoints.addFirst(new Checkpoint(new Vec3(spawn.x(), spawn.y(), spawn.z()), spawn.getYaw(), spawn.getPitch(), spawnBounds));

        checkpoints.addLast(goal);

        checkpointManager = new CheckpointManager(checkpoints, commons().debugController());
        checkpointManager.init(collisionDetector, movementObserver, getWorld());

        CheckpointHelper.notifyWhenReached(checkpointManager, gameHandle.getTranslations());

        movementObserver.whenEntering(goal.bounds(), this::onEnterGoal);
    }

    private synchronized void onEnterGoal(ServerPlayer player) {
        if (winManager.isGameOver() || !progress.getPath().isInLastSegment(player)) return;

        int round = progress.getRound(player);

        if (round < progress.getRounds()) {
            nextRound(player, round);
            return;
        }

        Fireworks.spawnGoalFirework(player);

        winnerData.add(player);

        for (ServerPlayer other : gameHandle.getParticipants()) {
            if (other == player) continue;

            double remaining = progress.getAbsoluteRemaining(other);

            distanceData.setScore(other, remaining);
        }

        winManager.complete();
    }

    private void nextRound(ServerPlayer player, int round) {
        progress.incrementRound(player);
        checkpointManager.resetCheckpoints(player);
        scoreboard.updateRoundDisplay(player);

        if (nextRoundSong != null) {
            MusicHelper.playSong(nextRoundSong, 0.5f, player, gameHandle.getServer(), gameHandle.getSharedSongCache(), gameHandle.getLogger());
        }

        var text = gameHandle.getTranslations().translateText("game.ap2.pig_race.round_title", Component.literal("#" + (round + 1)).withStyle(YELLOW))
                .formatted(AQUA)
                .translateFor(player);

        Title.get(player).title(Component.empty(), text, 10, 30, 10);

        ParticleHelper.spawnParticleFor(ParticleTypes.FIREWORK, player.getX(), player.getY(), player.getZ(),
                100, 1, 1, 1, 0.5, List.of(player));
    }

    private void openGate() {
        ServerLevel world = getWorld();

        BlockState air = Blocks.AIR.defaultBlockState();

        for (BlockBox bounds : schemaHolder.get().getGates()) {
            for (BlockPos pos : bounds) {
                world.setBlockAndUpdate(pos, air);
            }
        }
    }

    private void teleportPlayers(BlockBox bounds) {
        ServerLevel world = getWorld();

        var schema = schemaHolder.get();
        PositionRotation spawn = schema.getSpawn();
        float yaw = spawn.getYaw();

        for (ServerPlayer player : gameHandle.getParticipants()) {
            BlockPos pos = bounds.randomBlockPos(random);

            double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;

            pendingEntities.put(player.getUUID(), createPending(x, y, z, yaw));
            player.teleportTo(world, x, y, z, Set.of(), yaw, 0f, true);

            giveStick(player);
        }
    }

    private PendingEntity<?> createPending(double x, double y, double z, float yaw) {
        Function<ServerLevel, ? extends LivingEntity> factory = switch (variant) {
            case PIG -> world -> new Pig(EntityType.PIG, world);
            case STRIDER -> world -> new Strider(EntityType.STRIDER, world);
        };

        return new PendingEntity<>(x, y, z, yaw, factory);
    }

    private void giveStick(ServerPlayer player) {
        Translations translations = gameHandle.getTranslations();

        ItemStack stick = unbreakable(new ItemStack(switch (variant) {
            case PIG -> Items.CARROT_ON_A_STICK;
            case STRIDER -> Items.WARPED_FUNGUS_ON_A_STICK;
        }));

        stick.set(DataComponents.CUSTOM_NAME, translations.translateText(player, "game.ap2.pig_race.boost")
                .styled(style -> style.withItalic(false).applyFormat(GOLD)));

        player.getInventory().setItem(STICK_SLOT, stick);

        PlayerInventoryAccess.setSelectedSlot(player, STICK_SLOT);
    }

    private void giveResetItem(ServerPlayer player) {
        Translations translations = gameHandle.getTranslations();

        PlayerHead head = getWorld().registryAccess()
                .lookupOrThrow(ApRegistries.PLAYER_HEAD)
                .getOptional(PlayerHeads.REDSTONE_BLOCK_REFRESH)
                .orElseThrow();

        ItemStack reset = head.createStack();

        reset.set(DataComponents.CUSTOM_NAME, translations.translateText(player, "ap2.game.reset").formatted(RED)
                .styled(style -> style.withItalic(false)));

        player.getInventory().setItem(8, reset);

        PlayerInventoryAccess.setSelectedSlot(player, 4);
    }

    private boolean isVehicle(Entity vehicle) {
        return vehicle instanceof Pig || vehicle instanceof Strider;
    }

    private record PendingEntity<T extends LivingEntity>(double x, double y, double z, float yaw, Function<ServerLevel, T> factory) {

        public T create(ServerPlayer player) {
            ServerLevel world = player.level();

            T entity = factory.apply(world);
            entity.setInvulnerable(true);
            entity.setYBodyRot(yaw);
            entity.setPosRaw(x, y + 0.1, z);
            entity.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));

            world.addFreshEntity(entity);

            player.startRiding(entity, true, false);

            return entity;
        }
    }

    private enum Variant { PIG, STRIDER }
}
