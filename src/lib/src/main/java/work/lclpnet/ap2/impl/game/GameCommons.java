package work.lclpnet.ap2.impl.game;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.CombatEntry;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
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
import work.lclpnet.ap2.api.util.action.Action;
import work.lclpnet.ap2.core.mixin.entity.LivingEntityAccessor;
import work.lclpnet.ap2.game.MiniGameHandle;
import work.lclpnet.ap2.game.data.IntDataSink;
import work.lclpnet.ap2.game.util.HealthDisplay;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.resource.ApResources;
import work.lclpnet.ap2.impl.util.GameRuleBuilder;
import work.lclpnet.ap2.impl.util.debug.DebugController;
import work.lclpnet.ap2.impl.util.handler.Visibility;
import work.lclpnet.ap2.impl.util.handler.VisibilityHandler;
import work.lclpnet.ap2.impl.util.handler.VisibilityManager;
import work.lclpnet.ap2.impl.util.world.WorldBorderRandomizer;
import work.lclpnet.ap2.util.scoreboard.CustomScoreboardManager;
import work.lclpnet.gaco.math.Vec2i;
import work.lclpnet.game.map.GameMap;
import work.lclpnet.game.map.MapUtils;
import work.lclpnet.kibu.access.entity.ArmorStandAccess;
import work.lclpnet.kibu.access.entity.EntityUtil;
import work.lclpnet.kibu.access.entity.ServerPlayerAccess;
import work.lclpnet.kibu.access.misc.DamageTrackerAccess;
import work.lclpnet.kibu.hook.HookFactory;
import work.lclpnet.kibu.hook.util.PositionRotation;
import work.lclpnet.kibu.scheduler.api.TaskScheduler;
import work.lclpnet.kibu.translate.Translations;

import java.util.*;
import java.util.function.BiConsumer;

import static java.lang.Math.floor;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class GameCommons {

    private final MiniGameHandle gameHandle;
    private final GameMap map;
    private final ServerLevel world;
    private final DebugController debugController;
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

    public Action<Runnable> scheduleWorldBorderShrink(long delayTicks, long durationTicks, long finalDelayTicks) {
        return scheduleWorldBorderShrink(delayTicks, durationTicks, finalDelayTicks, new Random());
    }

    public Action<Runnable> scheduleWorldBorderShrink(long delayTicks, long durationTicks, long finalDelayTicks, Random random) {
        return scheduleWorldBorderShrink(readWorldBorderConfig(), delayTicks, durationTicks, finalDelayTicks, random);
    }

    public Action<Runnable> scheduleWorldBorderShrink(WorldBorderConfig config, long delayTicks, long durationTicks, long finalDelayTicks, Random random) {
        TaskScheduler scheduler = gameHandle.getScheduler();

        var hook = HookFactory.createArrayBacked(Runnable.class, callbacks -> () -> {
            for (Runnable callback : callbacks) {
                callback.run();
            }
        });

        scheduler.timeout(() -> startWorldBorderShrink(config, durationTicks, random), delayTicks);

        scheduler.timeout(() -> hook.invoker().run(), delayTicks + durationTicks + finalDelayTicks);

        return Action.create(hook);
    }

    public WorldBorder startWorldBorderShrink(WorldBorderConfig config, long durationTicks, Random random) {
        WorldBorder worldBorder = setupWorldBorder(config);

        if (config.randomCenter()) {
            var randomizer = new WorldBorderRandomizer(map, debugController);

            randomizer.randomizeCenter(worldBorder, config, random);
        }

        worldBorder.lerpSizeBetween(worldBorder.getSize(), config.minSize(), durationTicks, world.getGameTime());

        for (ServerPlayer player : PlayerLookup.level(world)) {
            ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 1, 0);
        }

        return worldBorder;
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
        double damagePerBlock = wbConfig.optDouble("damage-per-block", WorldBorderConfig.DEFAULT_DAMAGE_PER_BLOCK);

        return new WorldBorderConfig(centerX, centerZ, maxRadius, minSize, randomCenter, alignRandomCenter, damagePerBlock);
    }

    public WorldBorder setupWorldBorder(WorldBorderConfig config) {
        WorldBorder worldBorder = gameHandle.getWorldBorderManager().getWorldBorder();
        worldBorder.setCenter(config.centerX() + 0.5, config.centerZ() + 0.5);
        worldBorder.setSize(config.maxRadius());
        worldBorder.setSafeZone(0);
        worldBorder.setDamagePerBlock(config.damagePerBlock());

        return worldBorder;
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
                .withStyle(ChatFormatting.GREEN);

        player.sendOverlayMessage(msg);
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
        var marker = new ArmorStand(EntityTypes.ARMOR_STAND, world);
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

    public PlayerTeam hideNameTags() {
        var team = gameHandle.getScoreboardManager().createTeam("team");

        team.setNameTagVisibility(Team.Visibility.NEVER);

        gameHandle.getScoreboardManager().joinTeam(gameHandle.getParticipants(), team);

        return team;
    }

    public static <T extends LivingEntity> boolean handleCustomDeath(T entity, float health, BiConsumer<T, DamageSource> action) {
        if (health > 0) return false;

        // the entity is dying
        List<CombatEntry> recentDamage = DamageTrackerAccess.getRecentDamage(entity);

        int size = recentDamage.size();

        if (size == 0) {
            action.accept(entity, entity.damageSources().generic());
        } else {
            CombatEntry damageRecord = recentDamage.get(size - 1);
            DamageSource source = damageRecord.source();

            // try to use death protector
            if (((LivingEntityAccessor) entity).invokeCheckTotemDeathProtection(source)) {
                return true;
            }

            action.accept(entity, source);
        }

        return true;
    }

    public record WorldBorderConfig(
            int centerX,
            int centerZ,
            int maxRadius,
            int minSize,
            boolean randomCenter,
            boolean alignRandomCenter,
            double damagePerBlock
    ) {
        public static final double DEFAULT_DAMAGE_PER_BLOCK = 0.8;

        public WorldBorderConfig(int centerX, int centerZ, int maxRadius, int minSize, boolean randomCenter, boolean alignRandomCenter) {
            this(centerX, centerZ, maxRadius, minSize, randomCenter, alignRandomCenter, DEFAULT_DAMAGE_PER_BLOCK);
        }

        public double align(double v) {
            return alignRandomCenter ? floor(v) + 0.5 : v;
        }
    }
}
