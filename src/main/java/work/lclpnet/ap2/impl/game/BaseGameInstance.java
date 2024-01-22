package work.lclpnet.ap2.impl.game;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import net.minecraft.world.border.WorldBorder;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import work.lclpnet.ap2.api.base.WorldBorderManager;
import work.lclpnet.ap2.api.game.GameInfo;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.MiniGameInstance;
import work.lclpnet.ap2.api.map.MapFacade;
import work.lclpnet.ap2.base.ArcadeParty;
import work.lclpnet.ap2.impl.map.MapUtil;
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedPlayerBossBar;
import work.lclpnet.ap2.impl.util.effect.ApEffect;
import work.lclpnet.ap2.impl.util.effect.ApEffects;
import work.lclpnet.ap2.impl.util.math.Vec2i;
import work.lclpnet.combatctl.api.CombatStyle;
import work.lclpnet.kibu.hook.entity.EntityHealthCallback;
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks;
import work.lclpnet.kibu.hook.player.PlayerSpawnLocationCallback;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.bossbar.BossBarProvider;
import work.lclpnet.kibu.translate.bossbar.TranslatedBossBar;
import work.lclpnet.lobby.game.api.WorldFacade;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.util.ProtectorUtils;

import java.util.concurrent.CompletableFuture;

import static net.minecraft.util.Formatting.RED;

public abstract class BaseGameInstance implements MiniGameInstance {

    protected final MiniGameHandle gameHandle;
    @Nullable
    private ServerWorld world = null;
    @Nullable
    private GameMap map = null;
    @Nullable
    private volatile GameCommons commons = null;

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

        mapFacade.openRandomMap(gameId, new BootstrapMapOptions(this::createWorldBootstrap), this::onMapReady);
    }

    protected CompletableFuture<Void> createWorldBootstrap(ServerWorld world, GameMap map) {
        return CompletableFuture.runAsync(() -> bootstrapWorld(world, map));
    }

    protected void bootstrapWorld(ServerWorld world, GameMap map) {}

    protected void onMapReady(ServerWorld world, GameMap map) {
        this.world = world;
        this.map = map;

        applyMapEffects();

        resetPlayers();

        prepare();

        gameHandle.getGameScheduler().timeout(this::afterInitialDelay, getInitialDelay());
    }

    private void applyMapEffects() {
        Object prop = getMap().getProperty("effects");
        if (!(prop instanceof JSONArray array)) return;

        Logger logger = gameHandle.getLogger();
        PlayerUtil playerUtil = gameHandle.getPlayerUtil();

        for (Object obj : array) {
            if (!(obj instanceof String str)) {
                logger.warn("Invalid effect entry of type {}", obj.getClass().getSimpleName());
                continue;
            }

            Identifier id = new Identifier(str);
            ApEffect effect = ApEffects.tryFrom(id);

            if (effect == null) continue;

            playerUtil.enableEffect(effect);
        }
    }

    private void resetPlayers() {
        PlayerUtil playerUtil = gameHandle.getPlayerUtil();

        PlayerLookup.all(gameHandle.getServer()).forEach(playerUtil::resetPlayer);
    }

    private void afterInitialDelay() {
        gameHandle.getTranslations().translateText("ap2.go").formatted(RED)
                .acceptEach(PlayerLookup.all(gameHandle.getServer()), (player, text) -> {
                    Title.get(player).title(text, Text.empty(), 5, 20, 5);
                    player.playSound(SoundEvents.ENTITY_CHICKEN_EGG, SoundCategory.PLAYERS, 1, 0);
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
    }

    protected int getInitialDelay() {
        int players = gameHandle.getParticipants().getAsSet().size();
        return Ticks.seconds(5) + players * 10;
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
        gameHandle.getPlayerUtil().setDefaultCombatStyle(CombatStyle.OLD);
    }

    protected final WorldBorder useWorldBorder() {
        if (!(getMap().getProperty("world-border") instanceof JSONObject wbConfig)) {
            throw new IllegalStateException("Object property \"world-border\" not set in map properties");
        }

        int centerX = 0, centerZ = 0;

        if (wbConfig.has("center")) {
            Vec2i center = MapUtil.readVec2i(wbConfig.getJSONArray("center"));

            centerX = center.x();
            centerZ = center.z();
        }

        int radius = wbConfig.getInt("size");
        if (radius % 2 == 0) radius += 1;

        WorldBorderManager manager = gameHandle.getWorldBorderManager();
        manager.setupWorldBorder(getWorld());

        WorldBorder worldBorder = gameHandle.getWorldBorderManager().getWorldBorder();
        worldBorder.setCenter(centerX + 0.5, centerZ + 0.5);
        worldBorder.setSize(radius);
        worldBorder.setSafeZone(0);
        worldBorder.setDamagePerBlock(0.8);

        return worldBorder;
    }

    /**
     * Disables any form of healing. Damage is still allowed.
     */
    protected final void useNoHealing() {
        HookRegistrar hooks = gameHandle.getHookRegistrar();

        hooks.registerHook(EntityHealthCallback.HOOK, (entity, health)
                -> health > entity.getHealth());
    }

    protected final void useTaskDisplay() {
        GameInfo gameInfo = gameHandle.getGameInfo();
        TranslationService translations = gameHandle.getTranslations();
        Identifier id = gameInfo.identifier("task");

        TranslatedBossBar bossBar = translations.translateBossBar(id, gameInfo.getTaskKey(), gameInfo.getTaskArguments())
                .with(gameHandle.getBossBarProvider())
                .formatted(Formatting.GREEN);

        bossBar.setColor(BossBar.Color.GREEN);

        bossBar.addPlayers(PlayerLookup.all(gameHandle.getServer()));

        gameHandle.getBossBarHandler().showOnJoin(bossBar);
    }

    protected final DynamicTranslatedPlayerBossBar usePlayerDynamicTaskDisplay(Object... args) {
        GameInfo gameInfo = gameHandle.getGameInfo();
        Identifier id = ArcadeParty.identifier("task");
        String key = gameInfo.getTaskKey();

        TranslationService translations = gameHandle.getTranslations();
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

    protected final GameCommons commons() {
        if (commons != null) return commons;

        synchronized (this) {
            if (commons != null) return commons;

            commons = new GameCommons(gameHandle, getMap());
        }

        return commons;
    }

    protected abstract void prepare();

    protected abstract void ready();
}
