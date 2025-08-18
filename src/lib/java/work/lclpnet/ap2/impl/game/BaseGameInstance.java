package work.lclpnet.ap2.impl.game;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.data.DataManager;
import work.lclpnet.ap2.api.game.GameInfo;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.MiniGameInstance;
import work.lclpnet.ap2.api.map.MapBootstrap;
import work.lclpnet.ap2.api.map.MapBootstrapFunction;
import work.lclpnet.ap2.api.map.MapFacade;
import work.lclpnet.ap2.impl.map.ServerThreadMapBootstrap;
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedPlayerBossBar;
import work.lclpnet.ap2.impl.util.effect.ApEffect;
import work.lclpnet.ap2.impl.util.effect.ApEffects;
import work.lclpnet.ap2.impl.util.property.ApMapProperties;
import work.lclpnet.combatctl.impl.CombatStyles;
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
import work.lclpnet.lobby.game.api.WorldFacade;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.util.ProtectorUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static net.minecraft.util.Formatting.*;
import static work.lclpnet.ap2.impl.util.TranslationUtil.quote;

/// A game instance that:
/// - loads a random map for the mini-game
/// - provides the default onPrepare() and onReady() entry points with the countdown in between
/// - provides common mini-game behaviour configuration methods
/// - configures restrictive protection with bypass for creative operator players
/// - registers default hooks, e.g. for spectators, spawn location and map properties
///
/// Note that this game instance is not bound be of a specific type, i.e. subclasses can be ob type FFA, TEAM etc.
public abstract class BaseGameInstance implements MiniGameInstance {

    protected final MiniGameHandle gameHandle;
    protected final ApMapProperties mapProperties = new ApMapProperties();
    @Nullable
    private ServerWorld world = null;
    @Nullable
    private GameMap map = null;
    @Nullable
    private volatile GameCommons commons = null;
    private int countdownTime = 0;
    private int countdownValue = 0;
    private final Set<ApEffect> activeEffects = new HashSet<>();
    private boolean locatorBarEnabled = false;

    public BaseGameInstance(MiniGameHandle gameHandle) {
        this.gameHandle = gameHandle;
    }

    @Override
    public void start() {
        gameHandle.protect(config -> {
            config.disallowAll();

            ProtectorUtils.allowCreativeOperatorBypass(config);
        });

        registerDefaultHooks();

        openMap();
    }

    protected void openMap() {
        MapFacade mapFacade = gameHandle.getMapFacade();
        Identifier gameId = gameHandle.getGameInfo().getId();

        MapBootstrap bootstrap = getMapBootstrap();
        mapFacade.openRandomMap(gameId, new BootstrapMapOptions(bootstrap::createWorldBootstrap), this::onMapReady);
    }

    protected MapBootstrap getMapBootstrap() {
        // if a child class implements the MapBootstrap interface directly
        if (this instanceof MapBootstrap bootstrap) {
            return bootstrap;
        }

        // if a child class implements the MapBootstrapFunction interface
        if (this instanceof MapBootstrapFunction fun) {
            return new ServerThreadMapBootstrap(fun);
        }

        return MapBootstrap.NONE;
    }

    protected void onMapReady(ServerWorld world, GameMap map) {
        this.world = world;
        this.map = map;

        applyMapEffects();
        loadMapProperties();
        configureLocatorBar();

        resetPlayers();

        sendMapCredits();

        gameHandle.getDeathMessages().replaceVanillaDeathMessages(world, gameHandle.getHookRegistrar());

        prepare();

        int initialDelay = getInitialDelay();

        scheduleCountdown(initialDelay);

        gameHandle.getGameScheduler().timeout(this::afterInitialDelay, initialDelay);
    }

    private void configureLocatorBar() {
        if (locatorBarEnabled) return;

        gameHandle.getHookRegistrar().registerHook(PlayerWaypointCallback.HOOK, (player, waypoint) -> true);

        if (world != null) {
            world.getWaypointHandler().clear();
        }
    }

    private void sendMapCredits() {
        if (map == null) return;

        DataManager dataManager = gameHandle.getDataManager();

        TextTranslatable name = quote(lang -> Text.literal(map.getName(lang)).formatted(AQUA, BOLD));

        Text authors = Text.literal(map.getAuthors().stream()
                        .map(dataManager::string)
                        .collect(Collectors.joining(", ")))
                .formatted(YELLOW, BOLD);

        gameHandle.getTranslations().translateText("ap2.map.by", name, authors)
                .formatted(GREEN, BOLD)
                .sendTo(getWorld().getPlayers());
    }

    private void scheduleCountdown(int durationTicks) {
        countdownValue = Math.min(3, durationTicks / 20);

        if (countdownValue <= 0) return;

        gameHandle.getGameScheduler()
                .interval(this::tickCountdown, 1, durationTicks - countdownValue * 20L)
                .whenComplete(this::clearCountdown);
    }

    private void tickCountdown(RunningTask task) {
        int time = countdownTime++;

        if (time % 20 != 0) return;

        if (countdownValue <= 0) {
            task.cancel();
        }

        Formatting color = switch (countdownValue) {
            case 3 -> RED;
            case 2 -> GOLD;
            case 1 -> YELLOW;
            default -> GREEN;
        };

        var msg = Text.literal(String.valueOf(countdownValue--)).formatted(color, BOLD);

        for (ServerPlayerEntity player : PlayerLookup.all(gameHandle.getServer())) {
            player.sendMessage(msg, true);
        }
    }

    private void clearCountdown() {
        for (ServerPlayerEntity player : PlayerLookup.all(gameHandle.getServer())) {
            player.sendMessage(Text.empty(), true);
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
        gameHandle.getTranslations().translateText("ap2.go").formatted(RED)
                .acceptEach(PlayerLookup.all(gameHandle.getServer()), (player, text) -> {
                    Title.get(player).title(text, Text.empty(), 5, 20, 5);
                    player.playSoundToPlayer(SoundEvents.ENTITY_CHICKEN_EGG, SoundCategory.PLAYERS, 1, 0);
                });

        ready();
    }

    private void registerDefaultHooks() {
        HookRegistrar hooks = gameHandle.getHookRegistrar();
        WorldFacade worldFacade = gameHandle.getWorldFacade();
        PlayerUtil playerUtil = gameHandle.getPlayerUtil();

        hooks.registerHook(ServerLivingEntityHooks.ALLOW_DAMAGE, (entity, source, amount) -> {
            if (!source.isOf(DamageTypes.OUT_OF_WORLD) || !(entity instanceof ServerPlayerEntity player)) return true;

            if (player.isSpectator()) {
                worldFacade.teleport(player);
                return false;
            }

            return true;
        });

        hooks.registerHook(PlayerSpawnLocationCallback.HOOK, data -> playerUtil.resetPlayer(data.getPlayer()));

        hooks.registerHook(PlayerInteractionHooks.USE_BLOCK, (player, world1, hand, hitResult) -> {
            if (player.isCreative() || mapProperties.getBoolean(ApMapProperties.ALLOW_BLOCK_INTERACTION, true)) {
                return ActionResult.PASS;
            }

            return ActionResult.FAIL;
        });
    }

    protected int getInitialDelay() {
        int players = gameHandle.getParticipants().getAsSet().size();
        return PlayerUtil.getLoadingDelayTicks(players);
    }

    protected final ServerWorld getWorld() {
        if (world == null) {
            throw new IllegalStateException("World not loaded yet");
        }

        return world;
    }

    protected final GameMap getMap() {
        if (map == null) {
            throw new IllegalStateException("Map not loaded yet");
        }

        return map;
    }

    protected final void useSurvivalMode() {
        gameHandle.getPlayerUtil().setDefaultGameMode(GameMode.SURVIVAL);
    }

    protected final void useOldCombat() {
        gameHandle.getPlayerUtil().setDefaultCombatStyle(CombatStyles.CLASSIC);
    }

    /**
     * Disables any form of healing. Damage is still allowed.
     */
    protected final void useNoHealing() {
        HookRegistrar hooks = gameHandle.getHookRegistrar();

        hooks.registerHook(EntityHealthCallback.HOOK, (entity, health)
                -> health > entity.getHealth());
    }

    protected final TranslatedBossBar useTaskDisplay() {
        GameInfo gameInfo = gameHandle.getGameInfo();
        Translations translations = gameHandle.getTranslations();
        Identifier id = gameInfo.identifier("task");

        TranslatedBossBar bossBar = translations.translateBossBar(id, gameInfo.getTaskKey(), gameInfo.getTaskArguments())
                .with(gameHandle.getBossBarProvider())
                .formatted(Formatting.GREEN);

        bossBar.setColor(BossBar.Color.GREEN);

        bossBar.addPlayers(PlayerLookup.all(gameHandle.getServer()));

        gameHandle.getBossBarHandler().showOnJoin(bossBar);

        return bossBar;
    }

    protected final DynamicTranslatedPlayerBossBar usePlayerDynamicTaskDisplay(Object... args) {
        GameInfo gameInfo = gameHandle.getGameInfo();
        Identifier id = ApConstants.identifier("task");
        String key = gameInfo.getTaskKey();

        Translations translations = gameHandle.getTranslations();
        BossBarProvider provider = gameHandle.getBossBarProvider();

        var bossBar = new DynamicTranslatedPlayerBossBar(id, key, args, translations, provider)
                .formatted(Formatting.GREEN);

        bossBar.setColor(BossBar.Color.GREEN);
        bossBar.setPercent(1f);

        for (ServerPlayerEntity player : gameHandle.getParticipants()) {
            bossBar.add(player);
        }

        bossBar.init(gameHandle.getHookRegistrar());

        return bossBar;
    }

    protected void enableLocatorBar() {
        this.locatorBarEnabled = true;
    }

    /**
     * Get or create {@link GameCommons} for this game.
     * This method should only be called after the map is ready.
     * If the {@link GameCommons} already need to be accessed during bootstrap, {@link #commons(GameMap, ServerWorld)} should be used instead.
     * @return The {@link GameCommons} singleton in scope of this game instance.
     */
    protected final GameCommons commons() {
        return commons(getMap(), getWorld());
    }

    protected final GameCommons commons(GameMap map, ServerWorld world) {
        if (commons != null) return commons;

        synchronized (this) {
            if (commons != null) return commons;

            commons = new GameCommons(gameHandle, map, world);
        }

        return commons;
    }

    protected abstract void prepare();

    protected abstract void ready();
}
