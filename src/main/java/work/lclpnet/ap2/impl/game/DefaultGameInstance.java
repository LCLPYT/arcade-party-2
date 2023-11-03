package work.lclpnet.ap2.impl.game;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import net.minecraft.world.border.WorldBorder;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import work.lclpnet.ap2.api.base.ParticipantListener;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.MiniGameInstance;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.game.data.DataEntry;
import work.lclpnet.ap2.api.map.MapFacade;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.ap2.impl.util.SoundHelper;
import work.lclpnet.kibu.hook.entity.EntityHealthCallback;
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks;
import work.lclpnet.kibu.hook.player.PlayerSpawnLocationCallback;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.RunningTask;
import work.lclpnet.kibu.scheduler.api.SchedulerAction;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.text.RootText;
import work.lclpnet.kibu.translate.text.TranslatedText;
import work.lclpnet.lobby.game.api.WorldFacade;
import work.lclpnet.lobby.game.map.GameMap;
import work.lclpnet.lobby.game.util.ProtectorUtils;

import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

import static net.minecraft.util.Formatting.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public abstract class DefaultGameInstance implements MiniGameInstance, ParticipantListener {

    protected final MiniGameHandle gameHandle;
    @Nullable
    private ServerWorld world = null;
    @Nullable
    private GameMap map = null;
    private boolean gameOver = false;

    public DefaultGameInstance(MiniGameHandle gameHandle) {
        this.gameHandle = gameHandle;
    }

    @Override
    public void start() {
        gameHandle.protect(config -> {
            config.disallowAll();

            ProtectorUtils.allowCreativeOperatorBypass(config);
        });

        registerDefaultHooks();

        MapFacade mapFacade = gameHandle.getMapFacade();
        Identifier gameId = gameHandle.getGameInfo().getId();

        mapFacade.openRandomMap(gameId, this::onMapReady);
    }

    private void onMapReady(ServerWorld world, GameMap map) {
        this.world = world;
        this.map = map;

        resetPlayers();

        prepare();

        gameHandle.getScheduler().timeout(this::afterInitialDelay, getInitialDelay());
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

    @Override
    public ParticipantListener getParticipantListener() {
        return this;
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

        hooks.registerHook(PlayerSpawnLocationCallback.HOOK, data
                -> playerUtil.resetPlayer(data.getPlayer()));
    }

    protected void win(@Nullable ServerPlayerEntity player) {
        if (player == null) {
            winNobody();
            return;
        }

        win(Set.of(player));
    }

    protected void winNobody() {
        win(Set.of());
    }

    protected synchronized void win(Set<ServerPlayerEntity> winners) {
        if (this.gameOver) return;

        gameOver = true;

        gameHandle.protect(config -> {
            config.disallowAll();

            ProtectorUtils.allowCreativeOperatorBypass(config);
        });

        getData().freeze();

        deferAnnouncement(winners);
    }

    private void deferAnnouncement(Set<ServerPlayerEntity> winners) {
        TranslationService translations = gameHandle.getTranslations();
        MinecraftServer server = gameHandle.getServer();

        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
            var msg = translations.translateText(player, "ap2.game.winner_is");

            Title.get(player).title(Text.empty(), msg.formatted(DARK_GREEN), 5, 100, 5);

            player.sendMessage(msg.formatted(GRAY));
        }

        gameHandle.getScheduler().interval(new SchedulerAction() {
            int t = 0;
            int i = 0;

            @Override
            public void run(RunningTask info) {
                if (t++ == 0) {
                    i++;
                    SoundHelper.playSound(server, SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.RECORDS, 0.7f, 2f);
                }

                if (i < 5) {
                    if (t > 15) {
                        t = 0;
                    }
                }
                if (i > 4) {
                    if (t >= 5) {
                        t = 0;
                    }
                    if (i >= 8) {
                        info.cancel();
                        announceGameOver(winners);
                    }
                }
            }
        }, 1);
    }

    private void announceGameOver(Set<ServerPlayerEntity> winners) {
        announceWinners(winners);
        broadcastTop3();

        gameHandle.getScheduler().timeout(() -> gameHandle.complete(winners), Ticks.seconds(7));
    }

    private void announceWinners(Set<ServerPlayerEntity> winners) {
        if (winners.size() <= 1) {
            if (winners.isEmpty()) {
                announceDraw();
            } else {
                announceWinner(winners);
            }
        } else {
            announceFallback(winners);
        }
    }

    private void announceWinner(Set<ServerPlayerEntity> winners) {
        TranslationService translations = gameHandle.getTranslations();
        TranslatedText won = translations.translateText("ap2.won").formatted(DARK_GREEN);

        ServerPlayerEntity winner = winners.iterator().next();
        var winnerName = Text.literal(winner.getEntityName()).formatted(AQUA);

        for (ServerPlayerEntity player : PlayerLookup.all(gameHandle.getServer())) {
            if (player == winner) {
                playWinSound(player);
            } else {
                playLooseSound(player);
            }

            Title.get(player).title(winnerName, won.translateFor(player), 5, 100, 5);
        }
    }

    private void announceDraw() {
        TranslationService translations = gameHandle.getTranslations();
        TranslatedText won = translations.translateText("ap2.won").formatted(DARK_GREEN);

        TranslatedText nobody = translations.translateText("ap2.nobody").formatted(AQUA);

        for (ServerPlayerEntity player : PlayerLookup.all(gameHandle.getServer())) {
            playLooseSound(player);
            Title.get(player).title(nobody.translateFor(player), won.translateFor(player), 5, 100, 5);
        }
    }

    private void announceFallback(Set<ServerPlayerEntity> winners) {
        TranslationService translations = gameHandle.getTranslations();

        TranslatedText game_over = translations.translateText("ap2.game_over").formatted(AQUA);
        TranslatedText you_won = translations.translateText("ap2.you_won").formatted(DARK_GREEN);
        TranslatedText you_lost = translations.translateText("ap2.you_lost").formatted(DARK_RED);

        for (ServerPlayerEntity player : PlayerLookup.all(gameHandle.getServer())) {
            TranslatedText subtitle;

            if (winners.contains(player)) {
                subtitle = you_won;
                playWinSound(player);
            } else {
                subtitle = you_lost;
                playLooseSound(player);
            }

            Title.get(player).title(game_over.translateFor(player), subtitle.translateFor(player), 5, 100, 5);
        }
    }

    private static void playWinSound(ServerPlayerEntity player) {
        player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1, 0);
    }

    private static void playLooseSound(ServerPlayerEntity player) {
        player.playSound(SoundEvents.ENTITY_BLAZE_DEATH, SoundCategory.PLAYERS, 1, 1);
    }

    private void broadcastTop3() {
        DataContainer data = getData();

        var order = data.orderedEntries().toList();
        var placement = new HashMap<UUID, Integer>();

        for (int i = 0; i < order.size(); i++) {
            DataEntry entry = order.get(i);
            placement.put(entry.getPlayer().uuid(), i);
        }

        TranslationService translations = gameHandle.getTranslations();

        var sep = Text.literal(ApConstants.SEPERATOR).formatted(DARK_GREEN, STRIKETHROUGH, BOLD);
        int sepLength = ApConstants.SEPERATOR.length();

        var sepSm = Text.literal("-".repeat(sepLength)).formatted(DARK_GRAY, STRIKETHROUGH);

        for (ServerPlayerEntity player : PlayerLookup.all(gameHandle.getServer())) {
            String results = translations.translate(player, "ap2.results");
            int len = results.length() + 2;

            var resultsText = Text.literal(results).formatted(GREEN);

            if (len - 1 >= sepLength) {
                player.sendMessage(resultsText);
            } else {
                int times = (sepLength - len) / 2;
                String sepShort = "=".repeat(times);

                var msg = Text.empty()
                        .append(Text.literal(sepShort).formatted(DARK_GREEN, STRIKETHROUGH, BOLD))
                        .append(Text.literal("[").formatted(DARK_GREEN, BOLD))
                        .append(resultsText.formatted(BOLD))
                        .append(Text.literal("]").formatted(DARK_GREEN, BOLD))
                        .append(Text.literal(sepShort + (sepLength - 2 * times - len > 0 ? "=" : ""))
                                .formatted(DARK_GREEN, STRIKETHROUGH, BOLD));

                player.sendMessage(msg);
            }

            for (int i = 0; i < 3; i++) {
                if (order.size() <= i) break;

                DataEntry entry = order.get(i);
                var text = entry.toText(translations);

                MutableText msg = Text.literal("#%s ".formatted(i + 1)).formatted(YELLOW)
                        .append(Text.literal(entry.getPlayer().name()).formatted(GRAY));

                if (text != null) {
                    msg.append(" ").append(text.translateFor(player));
                }

                player.sendMessage(msg);
            }

            UUID uuid = player.getUuid();

            if (placement.containsKey(uuid)) {
                player.sendMessage(sepSm);

                int playerIndex = placement.get(uuid);
                DataEntry entry = order.get(playerIndex);

                var extra = entry.toText(translations);

                if (extra != null) {
                    RootText translatedExtra = extra.translateFor(player);
                    player.sendMessage(translations.translateText(player, "ap2.you_placed_value",
                            styled("#" + (playerIndex + 1), YELLOW),
                            translatedExtra.formatted(YELLOW)).formatted(GRAY));
                } else {
                    player.sendMessage(translations.translateText(player, "ap2.you_placed",
                            styled("#" + (playerIndex + 1), YELLOW)).formatted(GRAY));
                }
            }

            player.sendMessage(sep);
        }
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

    protected final void setDefaultGameMode(GameMode gameMode) {
        gameHandle.getPlayerUtil().setDefaultGameMode(gameMode);
    }

    protected final boolean isGameOver() {
        return gameOver;
    }

    /**
     * Instantly makes players who would have died spectators and reset them.
     */
    protected final void useSmoothDeath() {
        HookRegistrar hooks = gameHandle.getHookRegistrar();

        hooks.registerHook(EntityHealthCallback.HOOK, (entity, health) -> {
            if (!(entity instanceof ServerPlayerEntity player) || health > 0) return false;

            eliminate(player);

            return true;
        });
    }

    protected void eliminate(ServerPlayerEntity player) {
        Participants participants = gameHandle.getParticipants();
        TranslationService translations = gameHandle.getTranslations();

        if (participants.isParticipating(player)) {
            participants.remove(player);

            translations.translateText("ap2.game.elimated", styled(player.getEntityName(), YELLOW))
                    .formatted(GRAY)
                    .sendTo(PlayerLookup.all(gameHandle.getServer()));
        }

        WorldFacade worldFacade = gameHandle.getWorldFacade();
        PlayerUtil playerUtil = gameHandle.getPlayerUtil();

        playerUtil.resetPlayer(player);
        worldFacade.teleport(player);
    }

    protected final WorldBorder shrinkWorldBorder() {
        if (!(getMap().getProperty("world-border") instanceof JSONObject wbConfig)) {
            throw new IllegalStateException("Object property \"world-border\" not set in map properties");
        }

        int centerX = 0, centerZ = 0;

        if (wbConfig.has("center")) {
            JSONArray array = wbConfig.getJSONArray("center");

            if (array.length() < 2) {
                throw new IllegalStateException("Center property must have at least two elements");
            }

            centerX = array.getInt(0);
            centerZ = array.getInt(1);
        }

        int radius = wbConfig.getInt("radius");

        WorldBorder worldBorder = gameHandle.getWorldBorderManager().getWorldBorder();
        worldBorder.setCenter(centerX + 0.5, centerZ + 0.5);
        worldBorder.setSize(radius + 1);
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

    protected abstract DataContainer getData();

    protected abstract void prepare();

    protected abstract void ready();
}
