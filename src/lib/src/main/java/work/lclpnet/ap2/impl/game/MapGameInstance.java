package work.lclpnet.ap2.impl.game;

import kotlin.Unit;
import lombok.Getter;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.data.DataManager;
import work.lclpnet.ap2.game.GameInfo;
import work.lclpnet.ap2.game.MiniGameHandle;
import work.lclpnet.ap2.game.MiniGameInstance;
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedPlayerBossBar;
import work.lclpnet.ap2.impl.util.effect.ApEffect;
import work.lclpnet.ap2.impl.util.effect.ApEffects;
import work.lclpnet.ap2.impl.util.property.ApMapProperties;
import work.lclpnet.ap2.util.SubtitleCountdown;
import work.lclpnet.combatctl.impl.CombatStyles;
import work.lclpnet.game.api.WorldFacade;
import work.lclpnet.game.map.GameMap;
import work.lclpnet.game.map.MapUtils;
import work.lclpnet.game.util.BossBarTimer;
import work.lclpnet.game.util.ProtectorUtils;
import work.lclpnet.kibu.access.entity.ServerPlayerAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.entity.EntityHealthCallback;
import work.lclpnet.kibu.hook.entity.PlayerInteractionHooks;
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks;
import work.lclpnet.kibu.hook.player.PlayerSpawnLocationCallback;
import work.lclpnet.kibu.hook.player.PlayerWaypointCallback;
import work.lclpnet.kibu.scheduler.api.RunningTask;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.bossbar.BossBarProvider;
import work.lclpnet.kibu.translate.bossbar.TranslatedBossBar;
import work.lclpnet.kibu.translate.text.TextTranslatable;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static net.minecraft.ChatFormatting.*;
import static work.lclpnet.ap2.impl.util.TranslationUtil.quote;

/// A game instance that:
/// - loads a random map for the mini-game
/// - provides the default onPrepare() and onReady() entry points with the countdown in between
/// - provides common mini-game behaviour configuration methods
/// - configures restrictive protection with bypass for creative operator players
/// - registers default hooks, e.g. for spectators, spawn location and map properties
///
/// Note that this game instance is not bound be of a specific type, i.e. subclasses can be ob type FFA, TEAM etc.
public abstract class MapGameInstance implements MiniGameInstance {

    @Getter
    protected final MiniGameHandle gameHandle;
    protected final ApMapProperties mapProperties = new ApMapProperties();
    private final ServerLevel level;
    private final GameMap map;
    @Nullable
    private volatile GameCommons commons = null;
    private int countdownTime = 0;
    private int countdownValue = 0;
    private final Set<ApEffect> activeEffects = new HashSet<>();
    private boolean locatorBarEnabled = false;

    public MapGameInstance(MiniGameHandle gameHandle, ServerLevel level, GameMap map) {
        this.gameHandle = gameHandle;
        this.level = level;
        this.map = map;
    }

    @Override
    public void start() {
        gameHandle.protect(config -> {
            config.disallowAll();

            ProtectorUtils.allowCreativeOperatorBypass(config);
        });

        registerDefaultHooks();

        onMapReady();
    }

    protected void onMapReady() {
        applyMapEffects();
        loadMapProperties();
        configureLocatorBar();

        resetPlayers();
        teleportPlayers();

        sendMapCredits();

        gameHandle.getDeathMessages().replaceVanillaDeathMessages(level, gameHandle.getHooks());

        prepare();

        int initialDelay = getInitialDelay();

        var countdown = new SubtitleCountdown(
                gameHandle.getServer(),
                gameHandle.getScheduler(),
                _ -> Unit.INSTANCE,
                () -> PlayerLookup.all(gameHandle.getServer())
        );

        countdown.schedule(initialDelay, this::afterInitialDelay);
    }

    protected void teleportPlayers() {
        Vec3 spawn = MapUtils.getSpawnPosition(map);
        float yaw = MapUtils.getSpawnYaw(map);

        for (ServerPlayer player : PlayerLookup.all(gameHandle.getServer())) {
            player.teleportTo(level, spawn.x(), spawn.y(), spawn.z(), Set.of(), yaw, 0, true);
        }
    }

    private void configureLocatorBar() {
        if (locatorBarEnabled) return;

        PlayerWaypointCallback.HOOK.registerWith(gameHandle.getHooks(), (_, waypoint)
                -> waypoint instanceof ServerPlayer);  // hide players from locator by default

        if (level != null) {
            level.getWaypointManager().breakAllConnections();
        }
    }

    private void sendMapCredits() {
        if (map == null) return;

        DataManager dataManager = gameHandle.getDataManager();

        TextTranslatable name = quote(lang -> Component.literal(map.getName(lang)).withStyle(AQUA, BOLD));

        Component authors = Component.literal(map.getAuthors().stream()
                        .map(dataManager::string)
                        .collect(Collectors.joining(", ")))
                .withStyle(YELLOW, BOLD);

        gameHandle.getTranslations().translateText("ap2.map.by", name, authors)
                .formatted(GREEN, BOLD)
                .sendTo(getLevel().players());
    }

    private void scheduleCountdown(int durationTicks) {
        countdownValue = Math.min(3, durationTicks / 20);

        if (countdownValue <= 0) return;

        gameHandle.getScheduler()
                .interval(this::tickCountdown, 1, durationTicks - countdownValue * 20L)
                .whenComplete(this::clearCountdown);
    }

    private void tickCountdown(RunningTask task) {
        int time = countdownTime++;

        if (time % 20 != 0) return;

        if (countdownValue <= 0) {
            task.cancel();
        }

        ChatFormatting color = switch (countdownValue) {
            case 3 -> RED;
            case 2 -> GOLD;
            case 1 -> YELLOW;
            default -> GREEN;
        };

        var msg = Component.literal(String.valueOf(countdownValue--)).withStyle(color, BOLD);

        for (ServerPlayer player : PlayerLookup.all(gameHandle.getServer())) {
            player.sendOverlayMessage(msg);
        }
    }

    private void clearCountdown() {
        for (ServerPlayer player : PlayerLookup.all(gameHandle.getServer())) {
            player.sendOverlayMessage(Component.empty());
        }
    }

    private void loadMapProperties() {
        Object prop = getMap().getProperty("properties");
        if (!(prop instanceof JSONObject config)) return;

        Logger logger = gameHandle.getLogger();

        for (String key : config.keySet()) {
            Identifier id = Identifier.tryParse(key);

            if (id == null) {
                logger.warn("Invalid map property identifier {}", key);
                continue;
            }

            Object obj = config.get(key);

            mapProperties.set(id, obj);
        }
    }

    private void applyMapEffects() {
        Object prop = getMap().getProperty("effects");
        
        if (!(prop instanceof JSONArray array)) return;

        Set<ApEffect> effects = ApEffects.fromJson(array, gameHandle.getLogger());

        enableEffects(effects);
    }

    protected synchronized void enableEffects(Set<ApEffect> effects) {
        activeEffects.addAll(effects);

        PlayerUtil playerUtil = gameHandle.getPlayerUtil();

        for (ApEffect effect : effects) {
            playerUtil.enableEffect(effect);
        }
    }

    protected synchronized void disableEffects() {
        PlayerUtil playerUtil = gameHandle.getPlayerUtil();

        for (ApEffect effect : activeEffects) {
            playerUtil.disableEffect(effect);
        }

        activeEffects.clear();
    }

    private void resetPlayers() {
        PlayerUtil playerUtil = gameHandle.getPlayerUtil();

        PlayerLookup.all(gameHandle.getServer()).forEach(playerUtil::resetPlayer);
    }

    protected void afterInitialDelay() {
        PlayerLookup.all(gameHandle.getServer()).forEach(this::sendGo);

        go();
    }

    protected void sendGo(ServerPlayer player) {
        var text = gameHandle.getTranslations().translateText("ap2.go")
                .formatted(RED)
                .translateFor(player);

        Title.get(player).title(text, Component.empty(), 5, 20, 5);

        ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.CHICKEN_EGG, SoundSource.PLAYERS, 1, 0);
    }

    private void registerDefaultHooks() {
        HookRegistrar hooks = gameHandle.getHooks();
        WorldFacade worldFacade = gameHandle.getWorldFacade();
        PlayerUtil playerUtil = gameHandle.getPlayerUtil();

        ServerLivingEntityHooks.ALLOW_DAMAGE.registerWith(hooks, (entity, source, _) -> {
            if (!source.is(DamageTypes.FELL_OUT_OF_WORLD) || !(entity instanceof ServerPlayer player)) return true;

            if (player.isSpectator()) {
                worldFacade.teleport(player);
                return false;
            }

            return true;
        });

        PlayerSpawnLocationCallback.HOOK.registerWith(hooks, data -> playerUtil.resetPlayer(data.getPlayer()));

        PlayerInteractionHooks.USE_BLOCK.registerWith(hooks, (player, _, _, _) -> {
            if (player.isCreative() || mapProperties.getBoolean(ApMapProperties.ALLOW_BLOCK_INTERACTION, true)) {
                return InteractionResult.PASS;
            }

            return InteractionResult.FAIL;
        });
    }

    public int getInitialDelay() {
        int players = gameHandle.getParticipants().getAsSet().size();
        return PlayerUtil.getLoadingDelayTicks(players);
    }

    public final ServerLevel getLevel() {
        return level;
    }

    public final GameMap getMap() {
        return map;
    }

    protected final void useSurvivalMode() {
        gameHandle.getPlayerUtil().setDefaultGameMode(GameType.SURVIVAL);
    }

    protected final void useOldCombat() {
        gameHandle.getPlayerUtil().setDefaultCombatStyle(CombatStyles.CLASSIC);
    }

    /**
     * Disables any form of healing. Damage is still allowed.
     */
    protected final void useNoHealing() {
        HookRegistrar hooks = gameHandle.getHooks();

        EntityHealthCallback.HOOK.registerWith(hooks, (entity, health)
                -> health > entity.getHealth());
    }

    protected final TranslatedBossBar useTaskDisplay() {
        GameInfo gameInfo = gameHandle.getGameInfo();
        Translations translations = gameHandle.getTranslations();
        Identifier id = gameInfo.identifier("task");

        TranslatedBossBar bossBar = translations.translateBossBar(id, gameInfo.getTaskKey(), gameInfo.getTaskArguments())
                .with(gameHandle.getBossBarProvider())
                .formatted(ChatFormatting.GREEN);

        bossBar.setColor(BossEvent.BossBarColor.GREEN);

        bossBar.addPlayers(PlayerLookup.all(gameHandle.getServer()));

        gameHandle.getBossBarHandler().showOnJoin(bossBar);

        return bossBar;
    }

    protected final BossBarTimer useTaskTimer(int seconds) {
        var subject = gameHandle.getTranslations().translateText(gameHandle.getGameInfo().getTaskKey());

        return commons().createTimer(subject, seconds);
    }

    protected final DynamicTranslatedPlayerBossBar usePlayerDynamicTaskDisplay(Object... args) {
        return usePlayerDynamicDisplay(gameHandle.getGameInfo().getTaskKey(), args);
    }

    protected final DynamicTranslatedPlayerBossBar usePlayerDynamicDisplay(String key, Object... args) {
        Identifier id = ApConstants.identifier("task");

        Translations translations = gameHandle.getTranslations();
        BossBarProvider provider = gameHandle.getBossBarProvider();

        var bossBar = new DynamicTranslatedPlayerBossBar(id, key, args, translations, provider)
                .formatted(ChatFormatting.GREEN);

        bossBar.setColor(BossEvent.BossBarColor.GREEN);
        bossBar.setPercent(1f);

        for (ServerPlayer player : gameHandle.getParticipants()) {
            bossBar.add(player);
        }

        bossBar.init(gameHandle.getHooks());

        return bossBar;
    }

    protected void enableLocatorBar() {
        this.locatorBarEnabled = true;
    }

    /**
     * Get or create {@link GameCommons} for this game.
     * This method should only be called after the map is ready.
     * If the {@link GameCommons} already need to be accessed during bootstrap, {@link #commons(GameMap, ServerLevel)} should be used instead.
     * @return The {@link GameCommons} singleton in scope of this game instance.
     */
    public final GameCommons commons() {
        return commons(getMap(), getLevel());
    }

    protected final GameCommons commons(GameMap map, ServerLevel world) {
        if (commons != null) return commons;

        synchronized (this) {
            if (commons != null) return commons;

            commons = new GameCommons(gameHandle, map, world);
        }

        return commons;
    }

    protected final boolean isParticipating(ServerPlayer player) {
        return gameHandle.getParticipants().isParticipating(player);
    }

    protected final HookRegistrar getHooks() {
        return gameHandle.getHooks();
    }

    protected abstract void prepare();

    protected abstract void go();
}
