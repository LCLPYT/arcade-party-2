package work.lclpnet.ap2.game.dragon_escape;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.CombatEntry;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.MiniGameResults;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.core.mixin.LivingEntityAccessor;
import work.lclpnet.ap2.game.dragon_escape.kit.EnderPearlKit;
import work.lclpnet.ap2.game.dragon_escape.kit.LeapKit;
import work.lclpnet.ap2.game.dragon_escape.kit.WindChargeKit;
import work.lclpnet.ap2.impl.game.FFAGameInstance;
import work.lclpnet.ap2.impl.game.PseudoElimination;
import work.lclpnet.ap2.impl.game.data.CombinedDataContainer;
import work.lclpnet.ap2.impl.game.data.DoubleScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.OrderedDataContainer;
import work.lclpnet.ap2.impl.game.data.Ordering;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.game.kit.KitHandler;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.Fireworks;
import work.lclpnet.ap2.impl.util.TimeHelper;
import work.lclpnet.ap2.impl.util.debug.SplinePathDebugger;
import work.lclpnet.ap2.impl.util.handler.VisibilityHandler;
import work.lclpnet.ap2.impl.util.math.MathUtil;
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.ap2.impl.util.world.ChunkPersistence;
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape;
import work.lclpnet.gaco.math.SplinePath;
import work.lclpnet.kibu.access.misc.DamageTrackerAccess;
import work.lclpnet.kibu.hook.entity.EntityHealthCallback;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.hook.util.OnGroundDetector;
import work.lclpnet.kibu.hook.util.PlayerUtils;
import work.lclpnet.kibu.translate.text.TranslatedText;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;
import work.lclpnet.lobby.game.map.MapUtils;

import java.util.*;
import java.util.stream.Stream;

import static java.lang.Math.*;
import static net.minecraft.ChatFormatting.BOLD;
import static net.minecraft.ChatFormatting.YELLOW;
import static net.minecraft.core.SectionPos.posToSectionCoord;
import static work.lclpnet.kibu.hook.util.OnGroundDetector.isOnGroundServer;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class DragonEscapeInstance extends FFAGameInstance {

    private static final boolean
            DEBUG_PATH = false,
            DEBUG_PROGRESS = false;


    private final OrderedDataContainer<ServerPlayer, PlayerRef> completed = new OrderedDataContainer<>(PlayerRef::create);
    private final DoubleScoreDataContainer<ServerPlayer, PlayerRef> score = new DoubleScoreDataContainer<>(
            PlayerRef::create, Ordering.DESCENDING, "ap2.score.distance"
    );
    private final CombinedDataContainer<ServerPlayer, PlayerRef> data = new CombinedDataContainer<>(List.of(completed, score));
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
    private Objective progressObjective;

    public DragonEscapeInstance(MiniGameHandle gameHandle) {
        super(gameHandle);

        movementBlocker = new SimpleMovementBlocker(gameHandle.getScheduler());
        movementBlocker.setModifySpeedAttribute(false);

        useOldCombat();
    }

    @Override
    protected DataContainer<ServerPlayer, PlayerRef> getData() {
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
                .set(GameRules.SPAWN_MOBS, false);

        if (DEBUG_PATH) {
            debugPath();
        }
    }

    private boolean readProps() {
        JSONObject props = getMap().getProperties();

        JSONArray keypointsJson = props.getJSONArray("dragon-path");
        Logger logger = gameHandle.getLogger();

        var path = MapUtil.readSplinePath(keypointsJson, logger).orElse(null);

        if (path == null) {
            logger.error("Failed to create dragon path, aborting game...");
            gameHandle.complete(MiniGameResults.EMPTY);
            return false;
        }

        goalShape = MapUtil.readShape(props.getJSONObject("goal-shape"));

        Vec3 playerStartPos = MapUtil.readCenteredVec3d(props.getJSONArray("path-player-start"));
        playerStartProgress = path.getProgress(playerStartPos);

        Vec3 playerEndPos = MapUtil.readCenteredVec3d(props.getJSONArray("path-player-end"));
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
        dragonController.init(gameHandle.getScheduler());
    }

    private void setupTrackers() {
        for (ServerPlayer player : gameHandle.getParticipants()) {
            Vec3 anchor = path.getNearestPosition(player.position());

            trackers.put(player.getUUID(), new Tracker(anchor));
        }
    }

    private void setupKits(VisibilityHandler visibilityHandler) {
        kitHandler = KitHandler.create(gameHandle, getWorld(), kitHandle -> List.of(
                new LeapKit(kitHandle),
                new EnderPearlKit(kitHandle, path),
                new WindChargeKit(kitHandle)
        ));

        PlayerInteractionHooks.USE_ITEM.registerWith(gameHandle.getHooks(), (_player, world, hand) -> {
            if (!(_player instanceof ServerPlayer player)) return InteractionResult.PASS;

            ItemStack stack = player.getItemInHand(hand);

            if (itemUseAllowed || kitHandler.isKitSelector(stack) || visibilityHandler.isVisibilityChanger(stack)) {
                return InteractionResult.PASS;
            }

            if (stack.has(DataComponents.USE_COOLDOWN)) {
                player.getCooldowns().addCooldown(stack, 0);
            }

            PlayerUtils.syncPlayerItems(player);

            return InteractionResult.FAIL;
        });

        kitHandler.setup();
    }

    private void markChunksPersistent() {
        var persistence = new ChunkPersistence(getWorld(), gameHandle);

        final int SAMPLES = 1000;

        for (int i = 0; i < SAMPLES; i++) {
            double t = (double) i / (SAMPLES - 1);

            Vec3 pos = path.samplePosition(t);

            int cx = posToSectionCoord(pos.x());
            int cz = posToSectionCoord(pos.z());

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
        ).toList(), gameHandle.getScheduler());
    }

    private void teleportPlayers() {
        JSONObject shapeJson = getMap().getProperties().getJSONObject("spawn-shape");
        BlockShape spawnShape = MapUtil.readShape(shapeJson);

        List<BlockPos> spawnPool = new ArrayList<>();

        for (BlockPos pos : spawnShape) {
            spawnPool.add(pos.immutable());
        }

        if (spawnPool.isEmpty()) {
            gameHandle.getLogger().error("Spawn shape is empty");
            return;
        }

        List<BlockPos> spawns = new ArrayList<>(spawnPool);

        ServerLevel world = getWorld();
        float yaw = MapUtils.getSpawnYaw(getMap());

        for (ServerPlayer player : gameHandle.getParticipants()) {
            if (spawns.isEmpty()) {
                spawns.addAll(spawnPool);
            }

            BlockPos pos = spawns.remove(random.nextInt(spawns.size()));

            player.teleportTo(world, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), yaw, 0f, true);
        }
    }

    private void blockMovement() {
        movementBlocker.init(gameHandle.getHooks());

        for (ServerPlayer player : gameHandle.getParticipants()) {
            movementBlocker.disableMovement(player);
        }
    }

    private void unblockMovement() {
        for (ServerPlayer player : gameHandle.getParticipants()) {
            movementBlocker.enableMovement(player);
        }
    }

    private void setupScoreboard() {
        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();

        progressObjective = scoreboardManager.createObjective("progress", ObjectiveCriteria.DUMMY,
                Component.literal("Progress").withStyle(YELLOW, BOLD), ObjectiveCriteria.RenderType.INTEGER,
                StyledFormat.PLAYER_LIST_DEFAULT);

        for (ServerPlayer player : pseudoElimination.iterateParticipants()) {
            updatePlayerProgress(player);
        }

        scoreboardManager.setDisplay(DisplaySlot.LIST, progressObjective);
    }

    @Override
    protected void afterInitialDelay() {
        kitHandler.startKitSelectionTimer(commons(), super::afterInitialDelay);
    }

    @Override
    protected void go() {
        gameHandle.protect(config -> {
            config.allow(ProtectionTypes.ALLOW_DAMAGE, (entity, source) -> {
                if (!(entity instanceof ServerPlayer player)
                        || !gameHandle.getParticipants().isParticipating(player)
                        || inGoal.contains(player.getUUID())
                        || source.getEntity() instanceof ServerPlayer) {
                    return false;
                }

                if (source.getEntity() instanceof EnderDragon) {
                    softEliminateAndCheck(player);
                    return false;
                }

                return !source.is(DamageTypes.FIREWORKS);
            });

            config.allow(ProtectionTypes.EXPLOSION, arg -> arg.getDirectSourceEntity() instanceof WindCharge);
        });

        kitHandler.disableKitChanger();
        kitHandler.selectKitItem();

        setupSmoothDeath();
        unblockMovement();

        dragonController.startMoving(gameHandle.getScheduler());

        gameHandle.getScheduler().interval(this::tick, 1);

        startMs = milliTime();
        itemUseAllowed = true;
    }

    private void setupSmoothDeath() {
        EntityHealthCallback.HOOK.registerWith(gameHandle.getHooks(), (entity, health) -> {
            if (!(entity instanceof ServerPlayer player) || health > 0) return false;

            // the player is dying
            List<CombatEntry> recentDamage = DamageTrackerAccess.getRecentDamage(entity);

            int size = recentDamage.size();

            if (size == 0) {
                softEliminateAndCheck(player);
            } else {
                CombatEntry damageRecord = recentDamage.get(size - 1);
                DamageSource source = damageRecord.source();

                // try to use death protector
                if (((LivingEntityAccessor) player).invokeCheckTotemDeathProtection(source)) {
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

        for (ServerPlayer player : pseudoElimination.iterateParticipants()) {
            if (goalShape.contains(player.position()) && !inGoal.contains(player.getUUID()) && OnGroundDetector.isOnGroundServer(player)) {
                onReachGoal(player);

                check = true;
                continue;
            }

            double progress = getProgress(player);

            updateTracker(player, progress);
            updatePlayerProgress(player);

            // eliminate the player if they are behind the dragon or not in range of the path
            if ((pseudoElimination.isParticipating(player) && progress <= dragonController.getDragonProgress())
                    || !player.position().closerThan(path.samplePosition(progress), pathEliminationDistance)) {

                softEliminate(player);
                check = true;
            }

            player.setRemainingFireTicks(0);
        }

        if (check) {
            checkComplete();
        }
    }

    private synchronized void updateTracker(ServerPlayer player, double progress) {
        Tracker tracker = trackers.get(player.getUUID());

        if (tracker == null || progress <= tracker.maxProgress || !isOnGroundServer(player)) return;

        tracker.maxProgress = progress;
        tracker.anchor = path.samplePosition(progress);
    }

    private void onReachGoal(ServerPlayer player) {
        if (!inGoal.add(player.getUUID()) || winManager.isGameOver()) return;

        double time = (milliTime() - startMs) / 1000.d;
        TranslatedText duration = TimeHelper.formatTime(gameHandle.getTranslations(), time, "%02d", "%06.3f");

        completed.add(player, duration);

        gameHandle.getTranslations().translateText("game.ap2.dragon_escape.goal", styled(player.getScoreboardName(), ChatFormatting.YELLOW))
                .formatted(ChatFormatting.GREEN)
                .sendTo(PlayerLookup.all(gameHandle.getServer()));

        Fireworks.spawnGoalFirework(player);

        Tracker tracker = trackers.get(player.getUUID());

        if (tracker == null) return;

        tracker.maxProgress = 1;

        updatePlayerProgress(player);
    }

    private synchronized void softEliminateAndCheck(ServerPlayer player) {
        softEliminate(player);
        checkComplete();
    }

    private synchronized void softEliminate(ServerPlayer player) {
        if (pseudoElimination.eliminate(player) && !winManager.isGameOver()) {
            trackScore(player);
        }

        Tracker tracker = trackers.get(player.getUUID());

        if (tracker != null) {
            Vec3 pos = tracker.anchor;
            Vec3 dir = path.sampleDirection(tracker.maxProgress).normalize();

            player.teleportTo(getWorld(), pos.x(), pos.y(), pos.z(), Set.of(), MathUtil.yaw(dir), MathUtil.pitch(dir), true);
        }
    }

    private synchronized void trackScore(ServerPlayer player) {
        double distance = getDistance(player);

        if (distance > maxScore) {
            maxScore = distance;
        }

        score.setScore(player, distance);
    }

    private double getProgress(ServerPlayer player) {
        return path.getProgress(player.position());
    }

    private synchronized double getDistance(ServerPlayer player) {
        Tracker tracker = trackers.get(player.getUUID());

        double progress = tracker != null ? tracker.maxProgress : getProgress(player);

        return max(0, (progress - playerStartProgress) * path.getLength());
    }

    private void updatePlayerProgress(ServerPlayer player) {
        Tracker tracker = trackers.get(player.getUUID());

        if (tracker == null) return;

        double progress = getPlayerProgress(tracker.maxProgress);
        int percent = (int) floor(progress * 100);

        var format = new FixedFormat(Component.literal(percent + "%").withStyle(YELLOW));

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

        List<ServerPlayer> remaining = streamRemaining().toList();

        if (remaining.size() >= 2) return;

        if (remaining.size() == 1) {
            // check if the last remaining player is the furthest
            ServerPlayer last = remaining.getFirst();

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

    private @NotNull Stream<ServerPlayer> streamRemaining() {
        return pseudoElimination.streamParticipants()
                .filter(player -> !inGoal.contains(player.getUUID()));
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
        Vec3 anchor;

        Tracker(Vec3 anchor) {
            this.anchor = anchor;
        }
    }
}
