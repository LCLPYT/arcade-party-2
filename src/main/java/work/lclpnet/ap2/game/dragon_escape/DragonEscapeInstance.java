package work.lclpnet.ap2.game.dragon_escape;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.damage.DamageRecord;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.projectile.WindChargeEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.number.FixedNumberFormat;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.MiniGameResults;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.core.mixin.LivingEntityAccessor;
import work.lclpnet.ap2.game.dragon_escape.kit.*;
import work.lclpnet.ap2.impl.game.FFAGameInstance;
import work.lclpnet.ap2.impl.game.PseudoElimination;
import work.lclpnet.ap2.impl.game.data.CombinedDataContainer;
import work.lclpnet.ap2.impl.game.data.DoubleScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.OrderedDataContainer;
import work.lclpnet.ap2.impl.game.data.Ordering;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.game.kit.KitHandler;
import work.lclpnet.ap2.impl.game.kit.KitManager;
import work.lclpnet.ap2.impl.game.kit.ProxyKitReadView;
import work.lclpnet.ap2.impl.game.kit.RecordKitHandle;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.Fireworks;
import work.lclpnet.ap2.impl.util.SplinePath;
import work.lclpnet.ap2.impl.util.TimeHelper;
import work.lclpnet.ap2.impl.util.debug.SplinePathDebugger;
import work.lclpnet.ap2.impl.util.handler.VisibilityHandler;
import work.lclpnet.ap2.impl.util.math.MathUtil;
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.ap2.impl.util.world.ChunkPersistence;
import work.lclpnet.ap2.impl.util.world.stage.BlockShape;
import work.lclpnet.kibu.access.misc.DamageTrackerAccess;
import work.lclpnet.kibu.hook.entity.EntityHealthCallback;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.hook.util.OnGroundDetector;
import work.lclpnet.kibu.hook.util.PlayerUtils;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.translate.text.TranslatedText;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;
import work.lclpnet.lobby.game.map.MapUtils;

import java.util.*;
import java.util.stream.Stream;

import static java.lang.Math.*;
import static net.minecraft.util.Formatting.BOLD;
import static net.minecraft.util.Formatting.YELLOW;
import static net.minecraft.util.math.ChunkSectionPos.getSectionCoord;
import static work.lclpnet.kibu.hook.util.OnGroundDetector.isOnGroundServer;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class DragonEscapeInstance extends FFAGameInstance {

    private static final boolean
            DEBUG_PATH = false,
            DEBUG_PROGRESS = false;

    private static final int KIT_SELECTION_TICKS = Ticks.seconds(10);

    private final OrderedDataContainer<ServerPlayerEntity, PlayerRef> completed = new OrderedDataContainer<>(PlayerRef::create);
    private final DoubleScoreDataContainer<ServerPlayerEntity, PlayerRef> score = new DoubleScoreDataContainer<>(
            PlayerRef::create, Ordering.DESCENDING, "ap2.score.distance"
    );
    private final CombinedDataContainer<ServerPlayerEntity, PlayerRef> data = new CombinedDataContainer<>(List.of(completed, score));
    private final Random random = new Random();
    private final Set<UUID> inGoal = new HashSet<>();
    private final Map<UUID, Tracker> trackers = new HashMap<>();
    private final SimpleMovementBlocker movementBlocker;

    private long startMs = 0;
    private BlockShape goalShape = null;
    private SplinePath path = null;
    private DragonController dragonController = null;
    private PseudoElimination pseudoElimination = null;
    private double playerStartProgress = 0;
    private double playerPathLength = 0;
    private double pathEliminationDistance = 30;
    private double maxScore = Double.NEGATIVE_INFINITY;
    private boolean checkForCompletion = false;
    private boolean itemUseAllowed = false;
    private KitHandler kitHandler;
    private ScoreboardObjective progressObjective;

    public DragonEscapeInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        movementBlocker = new SimpleMovementBlocker(gameHandle.getGameScheduler());
        movementBlocker.setModifySpeedAttribute(false);

        useOldCombat();
    }

    @Override
    protected DataContainer<ServerPlayerEntity, PlayerRef> getData() {
        return data;
    }

    @Override
    protected void prepare() {
        if (!readProps()) return;

        pseudoElimination = new PseudoElimination(gameHandle, getWorld());

        markChunksPersistent();
        teleportPlayers();
        setupDragon();
        setupTrackers();
        blockMovement();
        setupScoreboard();

        VisibilityHandler visibilityHandler = commons().addVisibilityChanger(commons().noCollision());

        setupKits(visibilityHandler);

        commons().gameRuleBuilder()
                .set(GameRules.FALL_DAMAGE, false)
                .set(GameRules.DO_MOB_SPAWNING, false);

        if (DEBUG_PATH) {
            debugPath();
        }
    }

    private boolean readProps() {
        JSONObject props = getMap().getProperties();

        JSONArray keypointsJson = props.getJSONArray("dragon-path");
        Logger logger = gameHandle.getLogger();

        var path = SplinePath.readCentered(keypointsJson, logger).orElse(null);

        if (path == null) {
            logger.error("Failed to create dragon path, aborting game...");
            gameHandle.complete(MiniGameResults.EMPTY);
            return false;
        }

        goalShape = MapUtil.readShape(props.getJSONObject("goal-shape"));

        Vec3d playerStartPos = MapUtil.readCenteredVec3d(props.getJSONArray("path-player-start"));
        playerStartProgress = path.getProgress(playerStartPos);

        Vec3d playerEndPos = MapUtil.readCenteredVec3d(props.getJSONArray("path-player-end"));
        double playerEndProgress = path.getProgress(playerEndPos);

        playerPathLength = (playerEndProgress - playerStartProgress) * path.getLength();

        pathEliminationDistance = props.optDouble("path-elimination-distance", pathEliminationDistance);

        this.path = path;

        return true;
    }

    private void setupDragon() {
        dragonController = new DragonController(
                path, getWorld(), random,
                pos -> !goalShape.contains(pos),
                () -> pseudoElimination.iterateParticipants().iterator()
        );

        dragonController.spawnDragon();
        dragonController.init(gameHandle.getGameScheduler());
    }

    private void setupTrackers() {
        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            Vec3d anchor = path.getNearestPosition(player.getPos());

            trackers.put(player.getUuid(), new Tracker(anchor));
        }
    }

    private void setupKits(VisibilityHandler visibilityHandler) {
        var readView = new ProxyKitReadView();
        var handle = RecordKitHandle.of(gameHandle, getWorld().getRegistryManager(), readView);

        var manager = new KitManager(List.of(
                new LeapKit(handle),
                new EnderPearlKit(handle, path),
                new WindChargeKit(handle)
        ));

        readView.inject(manager);

        kitHandler = new KitHandler(manager, gameHandle.getParticipants(), gameHandle.getTranslations(), handle);

        gameHandle.getHookRegistrar().registerHook(PlayerInteractionHooks.USE_ITEM, (_player, world, hand) -> {
            if (!(_player instanceof ServerPlayerEntity player)) return ActionResult.PASS;

            ItemStack stack = player.getStackInHand(hand);

            if (itemUseAllowed || kitHandler.isKitSelector(stack) || visibilityHandler.isVisibilityChanger(stack)) {
                return ActionResult.PASS;
            }

            if (stack.contains(DataComponentTypes.USE_COOLDOWN)) {
                player.getItemCooldownManager().set(stack, 0);
            }

            PlayerUtils.syncPlayerItems(player);

            return ActionResult.FAIL;
        });

        manager.init();

        kitHandler.init(gameHandle.getHookRegistrar());
        kitHandler.setupPlayerKits();
        kitHandler.enableKitChanger();
        kitHandler.selectKitChanger();
    }

    private void markChunksPersistent() {
        var persistence = new ChunkPersistence(getWorld(), gameHandle);

        final int SAMPLES = 1000;

        for (int i = 0; i < SAMPLES; i++) {
            double t = (double) i / (SAMPLES - 1);

            Vec3d pos = path.samplePosition(t);

            int cx = getSectionCoord(pos.getX());
            int cz = getSectionCoord(pos.getZ());

            persistence.markPersistent(cx, cz);
        }
    }

    private void debugPath() {
        var debugger = new SplinePathDebugger(commons().debugController(), path);
        debugger.renderPath(1000);

        if (!DEBUG_PROGRESS) return;

        debugger.renderLiveProgress(() -> Stream.concat(
                pseudoElimination.streamParticipants(),
                dragonController.dragon().stream()
        ).toList(), gameHandle.getGameScheduler());
    }

    private void teleportPlayers() {
        JSONObject shapeJson = getMap().getProperties().getJSONObject("spawn-shape");
        BlockShape spawnShape = MapUtil.readShape(shapeJson);

        List<BlockPos> spawnPool = new ArrayList<>();

        for (BlockPos pos : spawnShape) {
            spawnPool.add(pos.toImmutable());
        }

        if (spawnPool.isEmpty()) {
            gameHandle.getLogger().error("Spawn shape is empty");
            return;
        }

        List<BlockPos> spawns = new ArrayList<>(spawnPool);

        ServerWorld world = getWorld();
        float yaw = MapUtils.getSpawnYaw(getMap());

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            if (spawns.isEmpty()) {
                spawns.addAll(spawnPool);
            }

            BlockPos pos = spawns.remove(random.nextInt(spawns.size()));

            player.teleport(world, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), yaw, 0f, true);
        }
    }

    private void blockMovement() {
        movementBlocker.init(gameHandle.getHookRegistrar());

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            movementBlocker.disableMovement(player);
        }
    }

    private void unblockMovement() {
        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            movementBlocker.enableMovement(player);
        }
    }

    private void setupScoreboard() {
        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();

        progressObjective = scoreboardManager.createObjective("progress", ScoreboardCriterion.DUMMY,
                Text.literal("Progress").formatted(YELLOW, BOLD), ScoreboardCriterion.RenderType.INTEGER,
                StyledNumberFormat.YELLOW);

        for (ServerPlayerEntity player : pseudoElimination.iterateParticipants()) {
            updatePlayerProgress(player);
        }

        scoreboardManager.setDisplay(ScoreboardDisplaySlot.LIST, progressObjective);
    }

    @Override
    protected void afterInitialDelay() {
        commons().announcer().announceSubtitle("ap2.kit_selector.hint");

        kitHandler.selectKitChanger();

        TranslatedText label = gameHandle.getTranslations().translateText("ap2.kit_selection");

        commons().createTimerTicks(label, KIT_SELECTION_TICKS).whenDone(super::afterInitialDelay);
    }

    @Override
    protected void ready() {
        gameHandle.protect(config -> {
            config.allow(ProtectionTypes.ALLOW_DAMAGE, (entity, source) -> {
                if (!(entity instanceof ServerPlayerEntity player)
                        || !gameHandle.getParticipants().isParticipating(player)
                        || inGoal.contains(player.getUuid())) {
                    return false;
                }

                if (source.getAttacker() instanceof EnderDragonEntity) {
                    softEliminateAndCheck(player);
                    return false;
                }

                return !source.isOf(DamageTypes.FIREWORKS);
            });

            config.allow(ProtectionTypes.EXPLOSION, arg -> arg.getEntity() instanceof WindChargeEntity);
        });

        kitHandler.disableKitChanger();
        kitHandler.selectKitItem();

        setupSmoothDeath();
        unblockMovement();

        dragonController.startMoving(gameHandle.getGameScheduler());

        gameHandle.getGameScheduler().interval(this::tick, 1);

        startMs = milliTime();
        itemUseAllowed = true;
    }

    private void setupSmoothDeath() {
        gameHandle.getHookRegistrar().registerHook(EntityHealthCallback.HOOK, (entity, health) -> {
            if (!(entity instanceof ServerPlayerEntity player) || health > 0) return false;

            // the player is dying
            List<DamageRecord> recentDamage = DamageTrackerAccess.getRecentDamage(entity);

            int size = recentDamage.size();

            if (size == 0) {
                softEliminateAndCheck(player);
            } else {
                DamageRecord damageRecord = recentDamage.get(size - 1);
                DamageSource source = damageRecord.damageSource();

                // try to use death protector
                if (((LivingEntityAccessor) player).invokeTryUseDeathProtector(source)) {
                    return true;
                }

                softEliminateAndCheck(player);
            }

            return true;
        });
    }

    private synchronized void tick() {
        if (winManager.isGameOver()) return;

        boolean check = checkForCompletion;

        for (ServerPlayerEntity player : pseudoElimination.iterateParticipants()) {
            if (goalShape.contains(player.getPos()) && !inGoal.contains(player.getUuid()) && OnGroundDetector.isOnGroundServer(player)) {
                onReachGoal(player);

                check = true;
                continue;
            }

            double progress = getProgress(player);

            updateTracker(player, progress);
            updatePlayerProgress(player);

            // eliminate the player if they are behind the dragon or not in range of the path
            if ((pseudoElimination.isParticipating(player) && progress <= dragonController.getDragonProgress())
                    || !player.getPos().isInRange(path.samplePosition(progress), pathEliminationDistance)) {

                softEliminate(player);
                check = true;
            }

            player.setFireTicks(0);
        }

        if (check) {
            checkComplete();
        }
    }

    private synchronized void updateTracker(ServerPlayerEntity player, double progress) {
        Tracker tracker = trackers.get(player.getUuid());

        if (tracker == null || progress <= tracker.maxProgress || !isOnGroundServer(player)) return;

        tracker.maxProgress = progress;
        tracker.anchor = path.samplePosition(progress);
    }

    private void onReachGoal(ServerPlayerEntity player) {
        if (!inGoal.add(player.getUuid()) || winManager.isGameOver()) return;

        double time = (milliTime() - startMs) / 1000.d;
        TranslatedText duration = TimeHelper.formatTime(gameHandle.getTranslations(), time, "%02d", "%06.3f");

        completed.add(player, duration);

        gameHandle.getTranslations().translateText("game.ap2.dragon_escape.goal", styled(player.getNameForScoreboard(), Formatting.YELLOW))
                .formatted(Formatting.GREEN)
                .sendTo(PlayerLookup.all(gameHandle.getServer()));

        Fireworks.spawnGoalFirework(player);

        Tracker tracker = trackers.get(player.getUuid());

        if (tracker == null) return;

        tracker.maxProgress = 1;

        updatePlayerProgress(player);
    }

    private synchronized void softEliminateAndCheck(ServerPlayerEntity player) {
        softEliminate(player);
        checkComplete();
    }

    private synchronized void softEliminate(ServerPlayerEntity player) {
        if (pseudoElimination.eliminate(player) && !winManager.isGameOver()) {
            trackScore(player);
        }

        Tracker tracker = trackers.get(player.getUuid());

        if (tracker != null) {
            Vec3d pos = tracker.anchor;
            Vec3d dir = path.sampleDirection(tracker.maxProgress).normalize();

            player.teleport(getWorld(), pos.getX(), pos.getY(), pos.getZ(), Set.of(), MathUtil.yaw(dir), MathUtil.pitch(dir), true);
        }
    }

    private synchronized void trackScore(ServerPlayerEntity player) {
        double distance = getDistance(player);

        if (distance > maxScore) {
            maxScore = distance;
        }

        score.setScore(player, distance);
    }

    private double getProgress(ServerPlayerEntity player) {
        return path.getProgress(player.getPos());
    }

    private synchronized double getDistance(ServerPlayerEntity player) {
        Tracker tracker = trackers.get(player.getUuid());

        double progress = tracker != null ? tracker.maxProgress : getProgress(player);

        return max(0, (progress - playerStartProgress) * path.getLength());
    }

    private void updatePlayerProgress(ServerPlayerEntity player) {
        Tracker tracker = trackers.get(player.getUuid());

        if (tracker == null) return;

        double progress = getPlayerProgress(tracker.maxProgress);
        int percent = (int) floor(progress * 100);

        var format = new FixedNumberFormat(Text.literal(percent + "%").formatted(YELLOW));

        gameHandle.getScoreboardManager().setNumberFormat(player, progressObjective, format);
    }

    private double getPlayerProgress(double progress) {
        double corrected = progress - playerStartProgress;

        return max(0.d, min(1.d, corrected * path.getLength() / playerPathLength));
    }

    private synchronized void checkComplete() {
        if (winManager.isGameOver()) return;

        if (inGoal.size() >= 3) {
            complete();
            return;
        }

        List<ServerPlayerEntity> remaining = streamRemaining().toList();

        if (remaining.size() >= 2) return;

        if (remaining.size() == 1) {
            // check if the last remaining player is the furthest
            ServerPlayerEntity last = remaining.getFirst();

            double distance = getDistance(last);

            if (distance > maxScore) {
                trackScore(last);
                complete();
                return;
            }

            checkForCompletion = true;
            return;
        }

        complete();
    }

    private @NotNull Stream<ServerPlayerEntity> streamRemaining() {
        return pseudoElimination.streamParticipants()
                .filter(player -> !inGoal.contains(player.getUuid()));
    }

    private synchronized void complete() {
        if (winManager.isGameOver()) return;

        streamRemaining().forEach(this::trackScore);

        winManager.complete();
    }

    private static long milliTime() {
        return System.nanoTime() / 1_000_000;
    }

    private static class Tracker {
        double maxProgress = 0;
        Vec3d anchor;

        Tracker(Vec3d anchor) {
            this.anchor = anchor;
        }
    }
}
