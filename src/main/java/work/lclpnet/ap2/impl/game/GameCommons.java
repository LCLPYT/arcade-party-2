package work.lclpnet.ap2.impl.game;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.world.border.WorldBorder;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.base.WorldBorderManager;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.sink.IntDataSink;
import work.lclpnet.ap2.api.util.action.Action;
import work.lclpnet.ap2.api.util.action.PlayerAction;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.GameRuleBuilder;
import work.lclpnet.ap2.impl.util.math.Vec2i;
import work.lclpnet.kibu.hook.HookFactory;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.player.PlayerMoveCallback;
import work.lclpnet.kibu.hook.util.PositionRotation;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.map.MapUtils;
import work.lclpnet.lobby.game.util.BossBarTimer;

import java.util.List;
import java.util.Objects;
import java.util.Random;

import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class GameCommons {

    private final MiniGameHandle gameHandle;
    private final GameMap map;
    private final ServerWorld world;
    private volatile Announcer announcer = null;
    private volatile List<PositionRotation> spawns = null;
    private volatile GameRuleBuilder gameRuleBuilder = null;

    public GameCommons(MiniGameHandle gameHandle, GameMap map, ServerWorld world) {
        this.gameHandle = gameHandle;
        this.map = map;
        this.world = world;
    }

    public Action<PlayerAction> whenBelowCriticalHeight() {
        Objects.requireNonNull(map);

        Number minY = map.getProperty("critical-height");

        if (minY == null) return Action.noop();

        return whenBelowY(minY.doubleValue());
    }

    public Action<PlayerAction> whenBelowY(double minY) {
        HookRegistrar hooks = gameHandle.getHookRegistrar();
        Participants participants = gameHandle.getParticipants();

        var hook = PlayerAction.createHook();

        hooks.registerHook(PlayerMoveCallback.HOOK, (player, from, to) -> {
            if (!participants.isParticipating(player)) return false;

            if (player.getY() < minY) {
                hook.invoker().act(player);
            }

            return false;
        });

        return Action.create(hook);
    }

    public Action<Runnable> scheduleWorldBorderShrink(long delayTicks, long durationTicks) {
        return scheduleWorldBorderShrink(delayTicks, durationTicks, 0);
    }

    public Action<Runnable> scheduleWorldBorderShrink(long delayTicks, long durationTicks, long finalDelayTicks) {
        TaskScheduler scheduler = gameHandle.getGameScheduler();

        var hook = HookFactory.createArrayBacked(Runnable.class, callbacks -> () -> {
            for (Runnable callback : callbacks) {
                callback.run();
            }
        });

        WorldBorderConfig config = readWorldBorderConfig();

        scheduler.timeout(() -> {
            WorldBorder worldBorder = setupWorldBorder(config);
            worldBorder.interpolateSize(worldBorder.getSize(), config.minRadius(), durationTicks * 50L);

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

        int minRadius = 5;

        if (wbConfig.has("min-size")) {
            minRadius = wbConfig.getInt("min-size");

            if (minRadius % 2 == 0) {
                minRadius += 1;
            }
        }

        int maxRadius = wbConfig.getInt("size");

        if (maxRadius % 2 == 0) {
            maxRadius += 1;
        }

        return new WorldBorderConfig(centerX, centerZ, maxRadius, minRadius);
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

    public BossBarTimer createTimer(Object subject, int durationSeconds) {
        return createTimer(subject, durationSeconds, BossBar.Color.RED);
    }

    public BossBarTimer createTimer(Object subject, int durationSeconds, BossBar.Color color) {
        Translations translations = gameHandle.getTranslations();

        BossBarTimer timer = BossBarTimer.builder(translations, subject)
                .withAlertSound(false)
                .withColor(color)
                .withDurationTicks(Ticks.seconds(durationSeconds))
                .build();

        timer.addPlayers(PlayerLookup.all(gameHandle.getServer()));
        timer.start(gameHandle.getBossBarProvider(), gameHandle.getGameScheduler());

        return timer;
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
        List<PositionRotation> spawns = getSpawns();

        if (spawns.isEmpty()) return;

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            PositionRotation spawn = spawns.get(random.nextInt(spawns.size()));
            player.teleport(world, spawn.getX(), spawn.getY(), spawn.getZ(), spawn.getYaw(), spawn.getPitch());
        }
    }

    @Nullable
    public PositionRotation teleportToRandomSpawn(ServerPlayerEntity player, Random random) {
        List<PositionRotation> spawns = getSpawns();

        if (spawns.isEmpty()) return null;

        PositionRotation spawn = spawns.get(random.nextInt(spawns.size()));
        player.teleport(world, spawn.getX(), spawn.getY(), spawn.getZ(), spawn.getYaw(), spawn.getPitch());

        return spawn;
    }

    public List<PositionRotation> getSpawns() {
        if (spawns != null) return spawns;

        synchronized (this) {
            if (spawns == null) {
                spawns = MapUtils.getSpawnPositionsAndRotation(map);
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

    public record WorldBorderConfig(int centerX, int centerZ, int maxRadius, int minRadius) {}
}
