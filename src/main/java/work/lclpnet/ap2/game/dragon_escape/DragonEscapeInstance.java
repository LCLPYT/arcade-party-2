package work.lclpnet.ap2.game.dragon_escape;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.MiniGameResults;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.impl.game.FFAGameInstance;
import work.lclpnet.ap2.impl.game.PseudoElimination;
import work.lclpnet.ap2.impl.game.data.CombinedDataContainer;
import work.lclpnet.ap2.impl.game.data.OrderedDataContainer;
import work.lclpnet.ap2.impl.game.data.ScoreDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.Fireworks;
import work.lclpnet.ap2.impl.util.SplinePath;
import work.lclpnet.ap2.impl.util.debug.SplinePathDebugger;
import work.lclpnet.ap2.impl.util.world.stage.BlockShape;
import work.lclpnet.kibu.translate.text.FormatWrapper;
import work.lclpnet.lobby.game.impl.prot.ProtectionTypes;
import work.lclpnet.lobby.game.map.MapUtils;

import java.util.*;
import java.util.stream.Stream;

import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class DragonEscapeInstance extends FFAGameInstance {

    private static final boolean
            DEBUG_PATH = true,
            DEBUG_PROGRESS = true;

    private final OrderedDataContainer<ServerPlayerEntity, PlayerRef> completed = new OrderedDataContainer<>(PlayerRef::create);
    private final ScoreDataContainer<ServerPlayerEntity, PlayerRef> score = new ScoreDataContainer<>(PlayerRef::create);
    private final CombinedDataContainer<ServerPlayerEntity, PlayerRef> data = new CombinedDataContainer<>(List.of(completed, score));
    private final Random random = new Random();
    private final Set<UUID> inGoal = new HashSet<>();

    private BlockShape goalShape = null;
    private SplinePath path = null;
    private DragonController dragonController = null;
    private PseudoElimination pseudoElimination = null;
    private double maxScore = Double.NEGATIVE_INFINITY;
    private boolean checkForCompletion = false;

    public DragonEscapeInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
    }

    @Override
    protected DataContainer<ServerPlayerEntity, PlayerRef> getData() {
        return data;
    }

    @Override
    protected void prepare() {
        JSONObject props = getMap().getProperties();

        JSONArray keypointsJson = props.getJSONArray("dragon-path");
        Logger logger = gameHandle.getLogger();

        var path = SplinePath.readCentered(keypointsJson, logger).orElse(null);

        if (path == null) {
            logger.error("Failed to create dragon path, aborting game...");
            gameHandle.complete(MiniGameResults.EMPTY);
            return;
        }

        goalShape = MapUtil.readShape(props.getJSONObject("goal-shape"));

        this.path = path;

        teleportPlayers();

        ServerWorld world = getWorld();
        pseudoElimination = new PseudoElimination(gameHandle, world);

        dragonController = new DragonController(path, world, random, pos -> !goalShape.contains(pos));
        dragonController.spawnDragon();
        dragonController.init(gameHandle.getGameScheduler());

        if (DEBUG_PATH) {
            debugPath(path);
        }
    }

    private void debugPath(SplinePath path) {
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

    @Override
    protected void ready() {
        gameHandle.protect(config -> config.allow(ProtectionTypes.ALLOW_DAMAGE, (entity, source) -> {
            if (entity instanceof ServerPlayerEntity player && source.getAttacker() instanceof EnderDragonEntity) {
                softEliminate(player);
                checkComplete();
            }

            return false;
        }));

        dragonController.startMoving(gameHandle.getGameScheduler());

        gameHandle.getGameScheduler().interval(this::tick, 1);
    }

    private synchronized void tick() {
        if (winManager.isGameOver()) return;

        boolean check = checkForCompletion;

        for (ServerPlayerEntity player : pseudoElimination.iterateParticipants()) {
            if (goalShape.contains(player.getPos()) && !inGoal.contains(player.getUuid())) {
                onReachGoal(player);

                check = true;
                continue;
            }

            if (getProgress(player) <= dragonController.getDragonProgress()) {
                softEliminate(player);
                check = true;
            }
        }

        if (check) {
            checkComplete();
        }
    }

    private void onReachGoal(ServerPlayerEntity player) {
        if (!inGoal.add(player.getUuid()) || winManager.isGameOver()) return;

        completed.add(player);  // TODO add time maybe?

        gameHandle.getTranslations().translateText("game.ap2.dragon_escape.goal", styled(player.getNameForScoreboard(), Formatting.YELLOW))
                .formatted(Formatting.GREEN)
                .sendTo(PlayerLookup.all(gameHandle.getServer()));

        Fireworks.spawnGoalFirework(player);
    }

    private synchronized void softEliminate(ServerPlayerEntity player) {
        if (!pseudoElimination.eliminate(player) || winManager.isGameOver()) return;

        double distance = getDistance(player);

        if (distance > maxScore) {
            maxScore = distance;
        }

        // TODO use double score container
        score.setScore(player, (int) Math.round(distance));

        pseudoElimination.eliminate(player);
    }

    private double getProgress(ServerPlayerEntity player) {
        return path.getProgress(player.getPos());
    }

    private double getDistance(ServerPlayerEntity player) {
        return getProgress(player) * path.getLength();
    }

    private synchronized void checkComplete() {
        if (winManager.isGameOver()) return;

        List<ServerPlayerEntity> remaining = pseudoElimination.streamParticipants()
                .filter(player -> !inGoal.contains(player.getUuid()))
                .toList();

        if (remaining.size() >= 2) return;

        if (remaining.size() == 1) {
            // check if the last remaining player is the furthest
            ServerPlayerEntity last = remaining.getFirst();

            double distance = getDistance(last);

            if (distance > maxScore) {
                completed.add(last, gameHandle.getTranslations().translateText(""));
                complete();
                return;
            }

            checkForCompletion = true;
            return;
        }

        complete();
    }

    private void complete() {
        if (winManager.isGameOver()) return;

        winManager.complete();
    }
}
