package work.lclpnet.ap2.game.red_light_green_light;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.json.JSONArray;
import org.json.JSONObject;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.impl.game.FFAGameInstance;
import work.lclpnet.ap2.impl.game.data.OrderedDataContainer;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.Fireworks;
import work.lclpnet.ap2.impl.util.movement.SimpleMovementBlocker;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.gaco.ds.BlockBox;
import work.lclpnet.kibu.access.entity.ServerPlayerAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.player.PlayerMoveCallback;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.bossbar.TranslatedBossBar;
import work.lclpnet.kibu.translate.text.LocalizedFormat;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapUtils;
import work.lclpnet.lobby.util.RayCaster;

import java.util.*;

import static java.lang.Math.clamp;
import static net.minecraft.ChatFormatting.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class RedLightGreenLightInstance extends FFAGameInstance implements Runnable {

    private static final int UNTIL_STOP_MIN_TICKS = 70, UNTIL_STOP_MAX_TICKS = 110;
    private static final int WARN_TIME_MIN_TICKS = 35, WARN_TIME_MAX_TICKS = 70;
    private static final int FROZEN_MIN_TICKS = 60, FROZEN_MAX_TICKS = 105;
    private static final int END_TIME_SECONDS = 15;
    private final SimpleMovementBlocker movementBlocker;
    private final OrderedDataContainer<ServerPlayer, PlayerRef> data = new OrderedDataContainer<>(PlayerRef::create);
    private final Random random = new Random();
    private final Set<UUID> inGoal = new HashSet<>();
    private final Set<UUID> moved = new HashSet<>();
    private final List<TrafficLight> trafficLights = new ArrayList<>();
    private final RLGLMovementDetector movementDetector = new RLGLMovementDetector();
    private MovementTracker tracker = null;
    private TranslatedBossBar taskBar = null;
    private BlockBox goal;
    private int timer = 0;
    private int warn = 0;
    private int go = 0;
    private int gameEnd = -1;

    public RedLightGreenLightInstance(MiniGameHandle gameHandle) {
        super(gameHandle);
        movementBlocker = new SimpleMovementBlocker(gameHandle.getScheduler());
    }

    @Override
    protected DataContainer<ServerPlayer, PlayerRef> getData() {
        return data;
    }

    @Override
    protected void prepare() {
        taskBar = useTaskDisplay();

        GameMap map = getMap();
        ServerLevel world = getWorld();

        goal = MapUtil.readBox(map.requireProperty("goal"));
        tracker = new MovementTracker(goal);

        BlockBox spawnArea = MapUtil.readBox(map.requireProperty("spawn-area"));
        float yaw = MapUtils.getSpawnYaw(map);

        for (ServerPlayer participant : gameHandle.getParticipants()) {
            Vec3 pos = spawnArea.randomPos(random);
            participant.teleportTo(world, pos.x(), pos.y(), pos.z(), Set.of(), yaw, 0f, true);
        }

        readTrafficLights();
        setTrafficLightStatus(EnumSet.noneOf(TrafficLight.Status.class));

        movementBlocker.init(gameHandle.getHooks());

        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();
        PlayerTeam team = scoreboardManager.createTeam("team");
        team.setCollisionRule(Team.CollisionRule.NEVER);
        scoreboardManager.joinTeam(gameHandle.getParticipants(), team);
    }

    @Override
    protected void go() {
        HookRegistrar hooks = gameHandle.getHooks();

        movementDetector.register(this::onMovedWhileRed);
        movementDetector.init(hooks);

        PlayerMoveCallback.HOOK.registerWith(hooks, (player, _, _) -> {
            onMove(player);
            return false;
        });

        openGate();
        scheduleNextStop();
        setStatus(TrafficLight.Status.GREEN);

        gameHandle.getScheduler().interval(this, 1);
    }

    private void readTrafficLights() {
        JSONArray json = getMap().getProperty("traffic-lights");

        if (json == null) return;

        trafficLights.clear();

        for (Object obj : json) {
            if (!(obj instanceof JSONObject jsonObj)) continue;

            trafficLights.add(TrafficLight.fromJson(jsonObj));
        }
    }

    private void setStatus(TrafficLight.Status status) {
        if (status == TrafficLight.Status.GREEN) {
            movementDetector.unfixAll();

            for (UUID uuid : moved) {
                ServerPlayer player = gameHandle.getServer().getPlayerList().getPlayer(uuid);

                if (player == null) continue;

                movementBlocker.enableMovement(player);
            }

            moved.clear();
        } else if (status == TrafficLight.Status.RED) {
            for (ServerPlayer player : gameHandle.getParticipants()) {
                if (inGoal.contains(player.getUUID())) continue;

                movementDetector.fixPosition(player);
            }
        }

        setTrafficLightStatus(EnumSet.of(status));

        taskBar.setColor(switch (status) {
            case RED -> BossEvent.BossBarColor.RED;
            case YELLOW -> BossEvent.BossBarColor.YELLOW;
            case GREEN -> BossEvent.BossBarColor.GREEN;
        });

        String key = switch (status) {
            case RED -> "game.ap2.red_light_green_light.stop";
            case YELLOW -> "game.ap2.red_light_green_light.warn";
            case GREEN -> "game.ap2.red_light_green_light.go";
        };

        var msg = gameHandle.getTranslations().translateText(key).formatted(BOLD, switch (status) {
            case RED -> RED;
            case YELLOW -> YELLOW;
            case GREEN -> GREEN;
        });

        ServerLevel world = getWorld();

        for (ServerPlayer player : PlayerLookup.level(world)) {
            switch (status) {
                case RED -> ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.BREEZE_SHOOT, SoundSource.NEUTRAL, 1f, 0.5f);
                case YELLOW -> ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1f, 0.5f);
                case GREEN -> ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.NEUTRAL, 1f, 1f);
            }

            Title.get(player).title(msg.translateFor(player));
        }
    }

    private void setTrafficLightStatus(EnumSet<TrafficLight.Status> status) {
        ServerLevel world = getWorld();

        for (TrafficLight light : trafficLights) {
            light.set(status, world);
        }
    }

    private void openGate() {
        BlockBox gate = MapUtil.readBox(getMap().requireProperty("gate"));
        ServerLevel world = getWorld();
        BlockState air = Blocks.AIR.defaultBlockState();

        for (BlockPos pos : gate) {
            world.setBlockAndUpdate(pos, air);
        }
    }

    private void onMove(ServerPlayer player) {
        if (timer <= 0 || inGoal.contains(player.getUUID()) || !gameHandle.getParticipants().isParticipating(player)) return;

        tracker.track(player);

        if (goal.contains(player.position())) {
            onGoalReached(player);
        }
    }

    private void onMovedWhileRed(ServerPlayer player) {
        if (winManager.isGameOver()
                || !gameHandle.getParticipants().isParticipating(player)
                || inGoal.contains(player.getUUID())
                || !moved.add(player.getUUID())) return;

        movementDetector.unfixPosition(player);
        punish(player);
    }

    private void punish(ServerPlayer player) {
        ServerLevel world = getWorld();

        double x = player.getX(), y = player.getY(), z = player.getZ();
        world.sendParticles(ParticleTypes.CRIT, x, y, z, 100, 0.1, 0.1, 0.1, 1);

        // find a suitable position to reset the player to
        Vec3 pos = tracker.getMostDistantPos(player);

        if (pos != null) {
            world.playSound(player, x, y, z, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.PLAYERS, 0.5f, 1f);

            x = pos.x();
            y = findSuitableY(world, pos);
            z = pos.z();

            player.teleportTo(world, x, y, z, Set.of(), player.getYRot(), player.getXRot(), true);
            ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.PLAYERS, 0.5f, 1f);
        } else {
            world.playSound(null, player.blockPosition(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.PLAYERS, 0.5f, 1f);
        }

        movementBlocker.disableMovement(player);

        var msg = gameHandle.getTranslations().translateText(player, "game.ap2.red_light_green_light.moved").formatted(RED);
        player.sendSystemMessage(msg);
    }

    private double findSuitableY(ServerLevel world, Vec3 pos) {
        var ctx = new RayCaster.GenericRaycastContext(pos, pos.subtract(0, 10, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE);
        BlockHitResult hit = RayCaster.rayCastBlockCollision(world, ctx);

        return ((int) Math.round(hit.getLocation().y() * 20)) / 20d;
    }

    private void onGoalReached(ServerPlayer player) {
        if (!inGoal.add(player.getUUID())) return;

        data.add(player);

        ServerLevel world = getWorld();

        Fireworks.spawnGoalFirework(player);

        Translations translations = gameHandle.getTranslations();

        if (inGoal.size() >= gameHandle.getParticipants().count()) {
            winManager.complete();
        } else if (gameEnd == -1) {
            translations.translateText("game.ap2.red_light_green_light.goal",
                            styled(player.getScoreboardName(), YELLOW),
                            styled(END_TIME_SECONDS, YELLOW))
                    .formatted(GREEN)
                    .sendTo(PlayerLookup.level(world));

            gameEnd = Ticks.seconds(END_TIME_SECONDS);
        }
    }

    private void scheduleNextStop() {
        timer = UNTIL_STOP_MIN_TICKS + random.nextInt(UNTIL_STOP_MAX_TICKS - UNTIL_STOP_MIN_TICKS + 1);

        int randomNextWarn = WARN_TIME_MIN_TICKS + random.nextInt(WARN_TIME_MAX_TICKS - WARN_TIME_MIN_TICKS + 1);
        warn = clamp(randomNextWarn, 1, timer - WARN_TIME_MIN_TICKS);

        go = FROZEN_MIN_TICKS + random.nextInt(FROZEN_MAX_TICKS - FROZEN_MIN_TICKS + 1);
    }

    @Override
    public void run() {
        if (gameEnd >= 0) {
            int ticksUntilEnd = gameEnd--;

            if (ticksUntilEnd % 20 == 0) {
                taskBar.setProgress(ticksUntilEnd / 20f / END_TIME_SECONDS);
            }

            if (ticksUntilEnd == 0) {
                gradePlayers();
                winManager.complete();
                return;
            }
        }

        int relTime = timer--;

        if (relTime < 0) {
            if (relTime == -go) {
                scheduleNextStop();
                setStatus(TrafficLight.Status.GREEN);
            }
            return;
        }

        if (relTime == 0) {
            setStatus(TrafficLight.Status.RED);
            return;
        }

        if (relTime == warn) {
            setStatus(TrafficLight.Status.YELLOW);
        }
    }

    private void gradePlayers() {
        Translations translations = gameHandle.getTranslations();

        // grade players who are not yet in the goal by their distance to the goal
        gameHandle.getParticipants().stream()
                .filter(player -> !inGoal.contains(player.getUUID()))
                .map(player -> {
                    double distanceSq = goal.squaredDistanceTo(player.position());
                    return new Grade(player, Math.sqrt(distanceSq));
                })
                .sorted(Comparator.comparingDouble(Grade::distance))
                .forEachOrdered(grade -> {
                    var detail = translations.translateText("ap2.score.blocks_away", LocalizedFormat.format("%.1f", grade.distance));
                    data.add(grade.player(), detail);
                });
    }

    private record Grade(ServerPlayer player, double distance) {}
}
