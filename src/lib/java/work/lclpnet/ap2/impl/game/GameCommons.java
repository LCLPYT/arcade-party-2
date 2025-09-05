package work.lclpnet.ap2.impl.game;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.waypoint.Waypoint;
import net.minecraft.world.waypoint.WaypointStyle;
import net.minecraft.world.waypoint.WaypointStyles;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.base.WorldBorderManager;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.sink.IntDataSink;
import work.lclpnet.ap2.api.util.action.Action;
import work.lclpnet.ap2.api.util.action.PlayerAction;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.resource.ApResources;
import work.lclpnet.ap2.impl.util.EntityUtil;
import work.lclpnet.ap2.impl.util.GameRuleBuilder;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.ap2.impl.util.handler.Visibility;
import work.lclpnet.ap2.impl.util.handler.VisibilityHandler;
import work.lclpnet.ap2.impl.util.handler.VisibilityManager;
import work.lclpnet.ap2.impl.util.math.Vec2i;
import work.lclpnet.ap2.impl.util.movement.TickMovementDetector;
import work.lclpnet.ap2.impl.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.ap2.impl.util.world.WorldBorderRandomizer;
import work.lclpnet.kibu.access.entity.ArmorStandAccess;
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

import static java.lang.Math.floor;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class GameCommons {

    private final MiniGameHandle gameHandle;
    private final GameMap map;
    private final ServerWorld world;
    private final DebugController debugController;
    private volatile Announcer announcer = null;
    private volatile List<PositionRotation> spawns = null;
    private volatile GameRuleBuilder gameRuleBuilder = null;
    private volatile HealthDisplay healthDisplay = null;

    public GameCommons(MiniGameHandle gameHandle, GameMap map, ServerWorld world) {
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
        Participants participants = gameHandle.getParticipants();

        var hook = PlayerAction.createHook();
        var detector = new TickMovementDetector(() -> participants);

        detector.register(player -> {
            if (!participants.isParticipating(player)) return;

            if (player.getY() < minY) {
                hook.invoker().act(player);
            }
        });

        detector.init(gameHandle.getGameScheduler(), gameHandle.getHooks());

        return Action.create(hook);
    }

    public Action<Runnable> scheduleWorldBorderShrink(long delayTicks, long durationTicks, long finalDelayTicks) {
        return scheduleWorldBorderShrink(delayTicks, durationTicks, finalDelayTicks, new Random());
    }

    public Action<Runnable> scheduleWorldBorderShrink(long delayTicks, long durationTicks, long finalDelayTicks, Random random) {
        TaskScheduler scheduler = gameHandle.getGameScheduler();

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

            worldBorder.interpolateSize(worldBorder.getSize(), config.minSize(), durationTicks * 50L);

            for (ServerPlayerEntity player : PlayerLookup.world(world)) {
                player.playSoundToPlayer(SoundEvents.ENTITY_WITHER_DEATH, SoundCategory.HOSTILE, 1, 0);
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
        WorldBorderManager manager = gameHandle.getWorldBorderManager();
        manager.setupWorldBorder(world);

        WorldBorder worldBorder = gameHandle.getWorldBorderManager().getWorldBorder();
        worldBorder.setCenter(config.centerX() + 0.5, config.centerZ() + 0.5);
        worldBorder.setSize(config.maxRadius());
        worldBorder.setSafeZone(0);
        worldBorder.setDamagePerBlock(0.8);

        return worldBorder;
    }

    public Action<Runnable> addTimer(BossBar bossBar, int durationSeconds) {
        return addTimerTicks(bossBar, durationSeconds * 20);
    }

    public Action<Runnable> addTimerTicks(BossBar bossBar, int durationTicks) {
        var onEnd = HookFactory.createArrayBacked(Runnable.class, ops -> () -> {
            for (Runnable op : ops) {
                op.run();
            }
        });

        gameHandle.getGameScheduler().interval(1, new SchedulerAction() {
            int timer = durationTicks;

            @Override
            public void run(RunningTask info) {
                if (timer-- <= 0) {
                    info.cancel();
                    bossBar.setPercent(0);
                    onEnd.invoker().run();
                    return;
                }

                if (timer % 20 == 0) {
                    bossBar.setPercent((timer / 20f / durationTicks));
                }
            }
        });

        return Action.create(onEnd);
    }

    public BossBarTimer createTimer(Object subject, int durationSeconds) {
        return createTimer(subject, durationSeconds, BossBar.Color.RED);
    }

    public BossBarTimer createTimer(Object subject, int durationSeconds, BossBar.Color color) {
        return createTimerTicks(subject, Ticks.seconds(durationSeconds), color);
    }

    public BossBarTimer createTimerTicks(Object subject, int durationTicks) {
        return createTimerTicks(subject, durationTicks, BossBar.Color.RED);
    }

    public BossBarTimer createTimerTicks(Object subject, int durationTicks, BossBar.Color color) {
        Translations translations = gameHandle.getTranslations();

        BossBarTimer timer = BossBarTimer.builder(translations, subject)
                .withAlertSound(false)
                .withColor(color)
                .withDurationTicks(durationTicks)
                .build();

        timer.addPlayers(PlayerLookup.all(gameHandle.getServer()));
        timer.start(gameHandle.getBossBarProvider(), gameHandle.getGameScheduler());

        return timer;
    }

    public Team noCollision() {
        CustomScoreboardManager scoreboardManager = gameHandle.getScoreboardManager();

        Team team = scoreboardManager.createTeam("team");
        team.setCollisionRule(AbstractTeam.CollisionRule.NEVER);

        scoreboardManager.joinTeam(gameHandle.getParticipants(), team);

        return team;
    }

    public VisibilityHandler addVisibilityChanger(Team team) {
        Translations translations = gameHandle.getTranslations();
        VisibilityHandler visibility = new VisibilityHandler(new VisibilityManager(team, Visibility.PARTIALLY_VISIBLE), translations, gameHandle.getParticipants());
        visibility.init(gameHandle.getHooks());

        visibility.giveItems();

        return visibility;
    }

    public void addScore(ServerPlayerEntity player, int score, IntDataSink<ServerPlayerEntity> data) {
        data.addScore(player, score);

        String key = score == 1 ? "ap2.gain_point" : "ap2.gain_points";

        var msg = gameHandle.getTranslations().translateText(player, key,
                        styled(score, Formatting.YELLOW),
                        styled(data.getScore(player), Formatting.AQUA))
                .formatted(Formatting.GREEN);

        player.sendMessage(msg, true);
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

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            if (spawns.isEmpty()) {
                spawns.addAll(pool);
            }

            PositionRotation spawn = spawns.remove(random.nextInt(spawns.size()));
            player.teleport(world, spawn.getX(), spawn.getY(), spawn.getZ(), Set.of(), spawn.getYaw(), spawn.getPitch(), true);
        }
    }

    @Nullable
    public PositionRotation teleportToRandomSpawn(ServerPlayerEntity player, Random random) {
        List<PositionRotation> spawns = getSpawns();

        if (spawns.isEmpty()) return null;

        PositionRotation spawn = spawns.get(random.nextInt(spawns.size()));
        player.teleport(world, spawn.getX(), spawn.getY(), spawn.getZ(), Set.of(), spawn.getYaw(), spawn.getPitch(), true);

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

    public void addWaypoint(Vec3d pos, int color) {
        addWaypoint(pos, color, WaypointStyles.DEFAULT);
    }

    public void addWaypoint(Vec3d pos, int color, RegistryKey<WaypointStyle> style) {
        var marker = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
        marker.setPosition(pos);
        ArmorStandAccess.setSmall(marker, true);
        ArmorStandAccess.setMarker(marker, true);
        marker.setInvisible(true);

        Waypoint.Config waypointConfig = marker.getWaypointConfig();
        waypointConfig.color = Optional.of(color);
        waypointConfig.style = style;
        EntityUtil.setAttribute(marker, EntityAttributes.WAYPOINT_TRANSMIT_RANGE, 500.0);

        world.spawnEntity(marker);
        world.getWaypointHandler().onTrack(marker);
    }

    public record WorldBorderConfig(int centerX, int centerZ, int maxRadius, int minSize, boolean randomCenter,
                                    boolean alignRandomCenter) {

        public double align(double v) {
            return alignRandomCenter ? floor(v) + 0.5 : v;
        }
    }
}
