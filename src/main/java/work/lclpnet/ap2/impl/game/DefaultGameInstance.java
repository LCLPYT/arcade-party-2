package work.lclpnet.ap2.impl.game;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.ParticipantListener;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.MiniGameInstance;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.game.data.DataEntry;
import work.lclpnet.ap2.api.map.MapFacade;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.text.RootText;
import work.lclpnet.kibu.translate.text.TranslatedText;
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

    private void onMapReady(ServerWorld world) {
        this.world = world;

        prepare();

        gameHandle.getScheduler().timeout(this::afterInitialDelay, getInitialDelay());
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
        gameHandle.getHookRegistrar().registerHook(ServerLivingEntityHooks.ALLOW_DAMAGE, (entity, source, amount) -> {
            if (!source.isOf(DamageTypes.OUT_OF_WORLD) || !(entity instanceof ServerPlayerEntity player)) return true;

            if (player.isSpectator()) {
                gameHandle.getWorldFacade().teleport(player);
                return false;
            }

            return true;
        });
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

    protected void win(Set<ServerPlayerEntity> winners) {
        gameHandle.protect(config -> {
            config.disallowAll();

            ProtectorUtils.allowCreativeOperatorBypass(config);
        });

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

    protected abstract DataContainer getData();

    protected abstract void prepare();

    protected abstract void ready();
}
