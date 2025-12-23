package work.lclpnet.ap2.impl.game;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import net.minecraft.world.waypoints.Waypoint;
import net.minecraft.world.waypoints.WaypointStyleAsset;
import net.minecraft.world.waypoints.WaypointStyleAssets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.sink.IntDataSink;
import work.lclpnet.ap2.api.util.action.Action;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.resource.ApResources;
import work.lclpnet.ap2.impl.util.EntityUtil;
import work.lclpnet.ap2.impl.util.GameRuleBuilder;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.ap2.impl.util.handler.Visibility;
import work.lclpnet.ap2.impl.util.handler.VisibilityHandler;
import work.lclpnet.ap2.impl.util.handler.VisibilityManager;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.ap2.impl.util.world.WorldBorderRandomizer;
import work.lclpnet.gaco.collisions.movement.TickMovementDetector;
import work.lclpnet.gaco.collisions.util.PlayerAction;
import work.lclpnet.gaco.math.Vec2i;
import work.lclpnet.kibu.access.entity.ArmorStandAccess;
import work.lclpnet.kibu.access.entity.ServerPlayerAccess;
import work.lclpnet.kibu.hook.HookFactory;
import work.lclpnet.kibu.hook.util.PositionRotation;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.RunningTask;
import work.lclpnet.kibu.scheduler.api.SchedulerAction;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapUtils;
import work.lclpnet.lobby.game.util.BossBarTimer;

import java.util.*;
import java.util.function.DoubleSupplier;

import static java.lang.Math.floor;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class GameCommons {

    private final MiniGameHandle gameHandle;
    private final GameMap map;
    private final ServerLevel world;
    private final DebugController debugController;
    private volatile Announcer announcer = null;
    private volatile List<PositionRotation> spawns = null;
    private volatile GameRuleBuilder gameRuleBuilder = null;
    private volatile HealthDisplay healthDisplay = null;

    public GameCommons(MiniGameHandle gameHandle, GameMap map, ServerLevel world) {
        this.gameHandle = gameHandle;
        this.map = map;
        this.world = world;

        debugController = new DebugController();

        if (ApConstants.DEBUG) {
            debugController.init(ApResources.getInstance(), world);
        }
    }

    public @NotNull DebugController debugController() {
        return debugController;
    }

    public Action<PlayerAction> whenBelowCriticalHeight() {
        Objects.requireNonNull(map);

        Number minY = map.getProperty("critical-height");

        if (minY == null) return Action.noop();

        return whenBelowY(minY.doubleValue());
    }

    public Action<PlayerAction> whenBelowY(double minY) {
        return whenBelowY(() -> minY);
    }

    public Action<PlayerAction> whenBelowY(DoubleSupplier minY) {
        Participants participants = gameHandle.getParticipants();

        var hook = PlayerAction.createHook();
        var detector = new TickMovementDetector(() -> participants);

        detector.register(player -> {
            if (!participants.isParticipating(player)) return;

            if (player.getY() < minY.getAsDouble()) {
                hook.invoker().act(player);
            }
        });

        detector.init(gameHandle.getScheduler(), gameHandle.getHooks());

        return Action.create(hook);
    }

    public Action<Runnable> scheduleWorldBorderShrink(long delayTicks, long durationTicks, long finalDelayTicks) {
        return scheduleWorldBorderShrink(delayTicks, durationTicks, finalDelayTicks, new Random());
    }

    public Action<Runnable> scheduleWorldBorderShrink(long delayTicks, long durationTicks, long finalDelayTicks, Random random) {
        TaskScheduler scheduler = gameHandle.getScheduler();

        var hook = HookFactory.createArrayBacked(Runnable.class, callbacks -> () -> {
            for (Runnable callback : callbacks) {
                callback.run();
            }
        });

        WorldBorderConfig config = readWorldBorderConfig();

        scheduler.timeout(() -> {
            WorldBorder worldBorder = setupWorldBorder(config);

            if (config.randomCenter()) {
                var randomizer = new WorldBorderRandomizer(map, debugController);

                randomizer.randomizeCenter(worldBorder, config, random);
            }

            worldBorder.lerpSizeBetween(worldBorder.getSize(), config.minSize(), durationTicks, world.getGameTime());

            for (ServerPlayer player : PlayerLookup.world(world)) {
                ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 1, 0);
            }
        }, delayTicks);

        scheduler.timeout(() -> hook.invoker().run(), delayTicks + durationTicks + finalDelayTicks);

        return Action.create(hook);
    }

    public WorldBorderConfig readWorldBorderConfig() {
        if (!(map.getProperty("world-border") instanceof JSONObject wbConfig)) {
            throw new IllegalStateException("Object property \"world-border\" not set in map properties");
        }

        int centerX = 0, centerZ = 0;

        if (wbConfig.has("center")) {
            Vec2i center = MapUtil.readVec2i(wbConfig.getJSONArray("center"));

            centerX = center.x();
            centerZ = center.z();
        }

        int minSize = 5;

        if (wbConfig.has("min-size")) {
            minSize = wbConfig.getInt("min-size");

            if (minSize % 2 == 0) {
                minSize += 1;
            }
        }

        int maxRadius = wbConfig.getInt("size");

        if (maxRadius % 2 == 0) {
            maxRadius += 1;
        }

        boolean randomCenter = wbConfig.optBoolean("random-center", false);
        boolean alignRandomCenter = wbConfig.optBoolean("align-random-center", true);

        return new WorldBorderConfig(centerX, centerZ, maxRadius, minSize, randomCenter, alignRandomCenter);
    }

    public WorldBorder setupWorldBorder(WorldBorderConfig config) {
        WorldBorder worldBorder = gameHandle.getWorldBorderManager().getWorldBorder();
        worldBorder.setCenter(config.centerX() + 0.5, config.centerZ() + 0.5);
        worldBorder.setSize(config.maxRadius());
        worldBorder.setSafeZone(0);
        worldBorder.setDamagePerBlock(0.8);

        return worldBorder;
    }

    public Action<Runnable> addTimer(BossEvent bossBar, int durationSeconds) {
        return addTimerTicks(bossBar, durationSeconds * 20);
    }

    public Action<Runnable> addTimerTicks(BossEvent bossBar, int durationTicks) {
        var onEnd = HookFactory.createArrayBacked(Runnable.class, ops -> () -> {
            for (Runnable op : ops) {
                op.run();
            }
        });

        gameHandle.getScheduler().interval(1, new SchedulerAction() {
            int timer = durationTicks;

            @Override
            public void run(RunningTask info) {
                if (timer-- <= 0) {
                    info.cancel();
                    bossBar.setProgress(0);
                    onEnd.invoker().run();
                    return;
                }

                if (timer % 20 == 0) {
                    bossBar.setProgress(((float) timer / durationTicks));
                }
            }
        });

        return Action.create(onEnd);
    }

    public BossBarTimer createTimer(Object subject, int durationSeconds) {
        return createTimer(subject, durationSeconds, BossEvent.BossBarColor.RED);
    }

    public BossBarTimer createTimer(Object subject, int durationSeconds, BossEvent.BossBarColor color) {
        return createTimerTicks(subject, Ticks.seconds(durationSeconds), color);
    }

    public BossBarTimer createTimerTicks(Object subject, int durationTicks) {
        return createTimerTicks(subject, durationTicks, BossEvent.BossBarColor.RED);
    }

    public BossBarTimer createTimerTicks(Object subject, int durationTicks, BossEvent.BossBarColor color) {
        Translations translations = gameHandle.getTranslations();

        BossBarTimer timer = BossBarTimer.builder(translations, subject)
                .withAlertSound(false)
                .withColor(color)
                .withDurationTicks(durationTicks)
                .build();

        timer.addPlayers(PlayerLookup.all(gameHandle.getServer()));
        timer.start(gameHandle.getBossBarProvider(), gameHandle.getScheduler());

        return timer;
    }

    public PlayerTeam noCollision() {
        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();

        PlayerTeam team = scoreboardManager.createTeam("team");
        team.setCollisionRule(Team.CollisionRule.NEVER);

        scoreboardManager.joinTeam(gameHandle.getParticipants(), team);

        return team;
    }

    public VisibilityHandler addVisibilityChanger(PlayerTeam team) {
        Translations translations = gameHandle.getTranslations();
        VisibilityHandler visibility = new VisibilityHandler(new VisibilityManager(team, Visibility.PARTIALLY_VISIBLE), translations, gameHandle.getParticipants());
        visibility.init(gameHandle.getHooks());

        visibility.giveItems();

        return visibility;
    }

    public void addScore(ServerPlayer player, int score, IntDataSink<ServerPlayer> data) {
        data.addScore(player, score);

        String key = score == 1 ? "ap2.gain_point" : "ap2.gain_points";

        var msg = gameHandle.getTranslations().translateText(player, key,
                        styled(score, ChatFormatting.YELLOW),
                        styled(data.getScore(player), ChatFormatting.AQUA))
                .formatted(ChatFormatting.GREEN);

        player.displayClientMessage(msg, true);
    }

    public Announcer announcer() {
        if (announcer != null) {
            return announcer.withDefaults();
        }

        synchronized (this) {
            if (announcer == null) {
                announcer = new Announcer(gameHandle.getTranslations(), gameHandle.getServer());
            }
        }

        return announcer.withDefaults();
    }

    public void teleportToRandomSpawns(Random random) {
        List<PositionRotation> pool = getSpawns();

        if (pool.isEmpty()) return;

        var spawns = new ArrayList<>(pool);

        for (ServerPlayer player : gameHandle.getParticipants()) {
            if (spawns.isEmpty()) {
                spawns.addAll(pool);
            }

            PositionRotation spawn = spawns.remove(random.nextInt(spawns.size()));
            player.teleportTo(world, spawn.x(), spawn.y(), spawn.z(), Set.of(), spawn.getYaw(), spawn.getPitch(), true);
        }
    }

    @Nullable
    public PositionRotation teleportToRandomSpawn(ServerPlayer player, Random random) {
        List<PositionRotation> spawns = getSpawns();

        if (spawns.isEmpty()) return null;

        PositionRotation spawn = spawns.get(random.nextInt(spawns.size()));
        player.teleportTo(world, spawn.x(), spawn.y(), spawn.z(), Set.of(), spawn.getYaw(), spawn.getPitch(), true);

        return spawn;
    }

    public List<PositionRotation> getSpawns() {
        if (spawns != null) return spawns;

        synchronized (this) {
            if (spawns == null) {
                spawns = List.copyOf(MapUtils.getSpawnPositionsAndRotation(map));
            }
        }

        return spawns;
    }

    public GameRuleBuilder gameRuleBuilder() {
        if (gameRuleBuilder != null) return gameRuleBuilder;

        synchronized (this) {
            if (gameRuleBuilder == null) {
                gameRuleBuilder = new GameRuleBuilder(world.getGameRules(), gameHandle.getServer());
            }
        }

        return gameRuleBuilder;
    }

    public void displayHealth() {
        if (healthDisplay != null) return;

        synchronized (this) {
            if (healthDisplay != null) return;

            healthDisplay = new HealthDisplay(gameHandle);
        }

        healthDisplay.setup(gameHandle.getHooks());
    }

    public void addWaypoint(Vec3 pos, int color) {
        addWaypoint(pos, color, WaypointStyleAssets.DEFAULT);
    }

    public void addWaypoint(Vec3 pos, int color, ResourceKey<WaypointStyleAsset> style) {
        var marker = new ArmorStand(EntityType.ARMOR_STAND, world);
        marker.setPos(pos);
        ArmorStandAccess.setSmall(marker, true);
        ArmorStandAccess.setMarker(marker, true);
        marker.setInvisible(true);

        Waypoint.Icon waypointConfig = marker.waypointIcon();
        waypointConfig.color = Optional.of(color);
        waypointConfig.style = style;
        EntityUtil.setAttribute(marker, Attributes.WAYPOINT_TRANSMIT_RANGE, 500.0);

        world.addFreshEntity(marker);
        world.getWaypointManager().trackWaypoint(marker);
    }

    public record WorldBorderConfig(
            int centerX,
            int centerZ,
            int maxRadius,
            int minSize,
            boolean randomCenter,
            boolean alignRandomCenter
    ) {
        public double align(double v) {
            return alignRandomCenter ? floor(v) + 0.5 : v;
        }
    }
}
