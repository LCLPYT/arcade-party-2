package work.lclpnet.ap2.impl.game;

import it.unimi.dsi.fastutil.objects.ObjectIntPair;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.MiniGameResults;
import work.lclpnet.ap2.api.game.data.DataContainer;
import work.lclpnet.ap2.api.game.data.GenericGameResult;
import work.lclpnet.ap2.api.game.data.PlayerSubjectRefFactory;
import work.lclpnet.ap2.api.game.data.SubjectRef;
import work.lclpnet.ap2.api.stats.SessionStatsRecorder;
import work.lclpnet.ap2.api.util.action.Action;
import work.lclpnet.ap2.impl.game.data.type.PlayerRef;
import work.lclpnet.ap2.impl.game.data.type.TeamRef;
import work.lclpnet.ap2.impl.util.SoundHelper;
import work.lclpnet.kibu.hook.HookFactory;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.RunningTask;
import work.lclpnet.kibu.scheduler.api.SchedulerAction;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.RootText;
import work.lclpnet.kibu.translate.text.TranslatedText;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static net.minecraft.util.Formatting.*;

public class WinSequence<T, Ref extends SubjectRef> {

    public static final int POST_GAME_SECONDS = 7;

    private final MiniGameHandle gameHandle;
    private final DataContainer<T, Ref> data;
    private final PlayerSubjectRefFactory<Ref> refs;
    private final GenericGameResult<Ref> winners;
    private final MiniGameResults.Status status;
    private final CompletableFuture<Optional<UUID>> statsId;

    public WinSequence(MiniGameHandle gameHandle, DataContainer<T, Ref> data, PlayerSubjectRefFactory<Ref> refs,
                       GenericGameResult<Ref> winners, MiniGameResults.Status status, CompletableFuture<Optional<UUID>> statsId) {
        this.gameHandle = gameHandle;
        this.data = data;
        this.refs = refs;
        this.winners = winners;
        this.status = status;
        this.statsId = statsId;
    }

    public Action<Runnable> start() {
        Translations translations = gameHandle.getTranslations();
        MinecraftServer server = gameHandle.getServer();

        for (ServerPlayerEntity player : PlayerLookup.all(server)) {
            var msg = translations.translateText(player, "ap2.game.winner_is");

            Title.get(player).title(Text.empty(), msg.formatted(DARK_GREEN), 5, 100, 5);

            player.sendMessage(msg.formatted(GRAY));
        }

        var hook = HookFactory.createArrayBacked(Runnable.class, actions -> () -> {
            for (Runnable action : actions) {
                action.run();
            }
        });

        gameHandle.getRootScheduler().interval(new SchedulerAction() {
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
                        announceGameOver();
                    }
                }
            }
        }, 1).whenComplete(() -> hook.invoker().run());

        return Action.create(hook);
    }

    private void announceGameOver() {
        announceWinners();
        broadcastResults();

        gameHandle.getRootScheduler().timeout(() -> {
            var resultsMap = winners.getPlayerResults().stream().collect(Collectors.toMap(
                    ObjectIntPair::left,
                    pair -> new MiniGameResults.PlayerResult(pair.left(), pair.rightInt())
            ));

            gameHandle.complete(new MiniGameResults(status, resultsMap));
        }, Ticks.seconds(POST_GAME_SECONDS));
    }

    private void announceWinners() {
        var subjects = winners.getWinningSubjects();

        if (subjects.size() > 1) {
            announceMultipleWinners(winners);
            return;
        }

        if (subjects.isEmpty()) {
            announceDraw();
            return;
        }

        Ref winner = winners.getWinningSubjects().iterator().next();
        announceWinner(winner);
    }

    private void announceWinner(Ref winner) {
        Translations translations = gameHandle.getTranslations();
        TranslatedText won = translations.translateText("ap2.won").formatted(DARK_GREEN);

        for (ServerPlayerEntity player : PlayerLookup.all(gameHandle.getServer())) {
            var ref = refs.create(player);

            Text winnerName = winner.getNameFor(player);

            if (winner.equals(ref)) {
                playWinSound(player);

                if (winner instanceof TeamRef) {
                    winnerName = translations.translateText("ap2.your_team").translateFor(player);
                }
            } else {
                playLooseSound(player);
            }

            if (winnerName.getStyle().getColor() == null) {
                winnerName = winnerName.copy().formatted(AQUA);
            }

            Title.get(player).title(winnerName, won.translateFor(player), 5, 100, 5);
        }
    }

    private void announceDraw() {
        Translations translations = gameHandle.getTranslations();
        TranslatedText won = translations.translateText("ap2.won").formatted(DARK_GREEN);

        TranslatedText nobody = translations.translateText("ap2.nobody").formatted(AQUA);

        for (ServerPlayerEntity player : PlayerLookup.all(gameHandle.getServer())) {
            playLooseSound(player);
            Title.get(player).title(nobody.translateFor(player), won.translateFor(player), 5, 100, 5);
        }
    }

    private void announceMultipleWinners(GenericGameResult<Ref> winners) {
        Translations translations = gameHandle.getTranslations();

        boolean teams = winners.getWinningSubjects().iterator().next() instanceof TeamRef;

        TranslatedText gameOver = translations.translateText("ap2.game_over").formatted(AQUA);
        TranslatedText youWon = translations.translateText(teams ? "ap2.your_team_won" : "ap2.you_won").formatted(DARK_GREEN);
        TranslatedText youLost = translations.translateText(teams ? "ap2.your_team_lost" : "ap2.you_lost").formatted(DARK_RED);

        var winningPlayersRefs = winners.getWinningPlayers();

        for (ServerPlayerEntity player : PlayerLookup.all(gameHandle.getServer())) {
            TranslatedText subtitle;

            PlayerRef ref = PlayerRef.create(player);

            if (winningPlayersRefs.contains(ref)) {
                subtitle = youWon;
                playWinSound(player);
            } else {
                subtitle = youLost;
                playLooseSound(player);
            }

            Title.get(player).title(gameOver.translateFor(player), subtitle.translateFor(player), 5, 100, 5);
        }
    }

    private static void playWinSound(ServerPlayerEntity player) {
        player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1, 0);
    }

    private static void playLooseSound(ServerPlayerEntity player) {
        player.playSoundToPlayer(SoundEvents.ENTITY_BLAZE_DEATH, SoundCategory.PLAYERS, 1, 1);
    }

    private void broadcastResults() {
        List<ObjectIntPair<Ref>> order = winners.getSubjectResults();

        var announcement = new ResultAnnouncement<>(gameHandle.getTranslations(), refs, order, data::getEntry);

        var statsId = this.statsId.getNow(Optional.empty()).orElse(null);

        for (ServerPlayerEntity player : PlayerLookup.all(gameHandle.getServer())) {
            if (statsId == null) {
                announcement.sendTop(5, player);
                continue;
            }

            var statsMsg = getStatsMessage(player, statsId);

            announcement.sendTop(5, player, statsMsg);
        }
    }

    private RootText getStatsMessage(ServerPlayerEntity player, UUID statsId) {
        var nbt = new NbtCompound();
        nbt.putString("id", statsId.toString());

        var translations = gameHandle.getTranslations();

        return translations.translateText(player, "ap2.view_stats")
                .append(" ↗")
                .styled(style -> style
                        .withFormatting(AQUA)
                        .withHoverEvent(new HoverEvent.ShowText(translations.translateText(player, "ap2.view_stats.click")))
                        .withClickEvent(new ClickEvent.Custom(SessionStatsRecorder.SHOW_SUMMARY, Optional.of(nbt))));
    }
}
