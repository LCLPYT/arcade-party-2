package work.lclpnet.ap2.impl.game;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import work.lclpnet.ap2.api.game.MiniGameHandle;
import work.lclpnet.ap2.api.game.data.*;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.ap2.impl.game.data.type.TeamRef;
import work.lclpnet.ap2.impl.util.SoundHelper;
import work.lclpnet.kibu.scheduler.Ticks;
import work.lclpnet.kibu.scheduler.api.RunningTask;
import work.lclpnet.kibu.scheduler.api.SchedulerAction;
import work.lclpnet.kibu.title.Title;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.text.RootText;
import work.lclpnet.kibu.translate.text.TranslatedText;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.minecraft.util.Formatting.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class WinSequence<T, Ref extends SubjectRef> {

    private final MiniGameHandle gameHandle;
    private final DataContainer<T, Ref> data;
    private final PlayerSubjectRefFactory<Ref> refs;

    public WinSequence(MiniGameHandle gameHandle, DataContainer<T, Ref> data, PlayerSubjectRefFactory<Ref> refs) {
        this.gameHandle = gameHandle;
        this.data = data;
        this.refs = refs;
    }

    public void start(GameWinners<Ref> winners) {
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

    private void announceGameOver(GameWinners<Ref> winners) {
        announceWinners(winners);
        broadcastTop3();

        gameHandle.getScheduler().timeout(() -> gameHandle.complete(winners.getPlayers()), Ticks.seconds(7));
    }

    private void announceWinners(GameWinners<Ref> winners) {
        var subjects = winners.getSubjects();

        if (subjects.size() > 1) {
            announceMultipleWinners(winners);
            return;
        }

        if (subjects.isEmpty()) {
            announceDraw();
            return;
        }

        Ref winner = winners.getSubjects().iterator().next();
        announceWinner(winner);
    }

    private void announceWinner(Ref winner) {
        TranslationService translations = gameHandle.getTranslations();
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
        TranslationService translations = gameHandle.getTranslations();
        TranslatedText won = translations.translateText("ap2.won").formatted(DARK_GREEN);

        TranslatedText nobody = translations.translateText("ap2.nobody").formatted(AQUA);

        for (ServerPlayerEntity player : PlayerLookup.all(gameHandle.getServer())) {
            playLooseSound(player);
            Title.get(player).title(nobody.translateFor(player), won.translateFor(player), 5, 100, 5);
        }
    }

    private void announceMultipleWinners(GameWinners<Ref> winners) {
        TranslationService translations = gameHandle.getTranslations();

        boolean teams = winners.getSubjects().iterator().next() instanceof TeamRef;

        TranslatedText gameOver = translations.translateText("ap2.game_over").formatted(AQUA);
        TranslatedText youWon = translations.translateText(teams ? "ap2.your_team_won" : "ap2.you_won").formatted(DARK_GREEN);
        TranslatedText youLost = translations.translateText(teams ? "ap2.your_team_lost" : "ap2.you_lost").formatted(DARK_RED);

        var winningPlayers = winners.getPlayers();

        for (ServerPlayerEntity player : PlayerLookup.all(gameHandle.getServer())) {
            TranslatedText subtitle;

            if (winningPlayers.contains(player)) {
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
        player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1, 0);
    }

    private static void playLooseSound(ServerPlayerEntity player) {
        player.playSound(SoundEvents.ENTITY_BLAZE_DEATH, SoundCategory.PLAYERS, 1, 1);
    }

    private void broadcastTop3() {
        var order = data.orderedEntries().toList();
        var placement = new HashMap<Ref, Integer>();
        var entryByRef = new HashMap<Ref, DataEntry<Ref>>();

        int rank = 1;
        DataEntry<Ref> lastEntry = null;

        for (DataEntry<Ref> entry : order) {
            if (lastEntry != null && !entry.scoreEquals(lastEntry)) {
                rank++;
            }

            lastEntry = entry;

            Ref ref = entry.subject();

            placement.put(ref, rank);
            entryByRef.put(ref, entry);
        }

        for (ServerPlayerEntity player : PlayerLookup.all(gameHandle.getServer())) {
            sendTop3(player, order, placement, entryByRef);
        }
    }

    private void sendTop3(ServerPlayerEntity player, List<? extends DataEntry<Ref>> order, Map<Ref, Integer> placement,
                          HashMap<Ref, DataEntry<Ref>> entries) {
        TranslationService translations = gameHandle.getTranslations();
        String results = translations.translate(player, "ap2.results");
        int len = results.length() + 2;

        var resultsText = Text.literal(results).formatted(GREEN);

        var sep = Text.literal(ApConstants.SEPARATOR).formatted(DARK_GREEN, STRIKETHROUGH, BOLD);
        int sepLength = ApConstants.SEPARATOR.length();
        var sepSm = Text.literal("-".repeat(sepLength)).formatted(DARK_GRAY, STRIKETHROUGH);

        sendUpperSeparator(player, len, sepLength, resultsText);

        int maxRank = 1;

        for (int i = 0; i < 3; i++) {
            if (order.size() <= i) break;

            var entry = order.get(i);
            var text = entry.toText(translations);

            Ref subject = entry.subject();
            Text subjectName = subject.getNameFor(player);

            if (subjectName.getStyle().getColor() == null) {
                subjectName = subjectName.copy().formatted(GRAY);
            }

            int rank = placement.getOrDefault(subject, maxRank++);
            maxRank = Math.max(maxRank, rank);

            MutableText msg = Text.literal("#%s ".formatted(rank)).formatted(YELLOW)
                    .append(subjectName);

            if (text != null) {
                msg.append(" ").append(text.translateFor(player));
            }

            player.sendMessage(msg);
        }

        var ownRef = refs.create(player);

        if (ownRef != null && placement.containsKey(ownRef)) {
            int playerIndex = placement.get(ownRef);
            DataEntry<Ref> entry = entries.get(ownRef);

            sendOwnScore(player, entry, playerIndex, sepSm);
        }

        player.sendMessage(sep);
    }

    private void sendUpperSeparator(ServerPlayerEntity player, int len, int sepLength, MutableText resultsText) {
        if (len - 1 >= sepLength) {
            player.sendMessage(resultsText);
            return;
        }

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

    private void sendOwnScore(ServerPlayerEntity player, DataEntry<Ref> entry, int ranking, MutableText sepSm) {
        TranslationService translations = gameHandle.getTranslations();
        player.sendMessage(sepSm);

        var extra = entry.toText(translations);

        if (extra == null) {
            player.sendMessage(translations.translateText(player, "ap2.you_placed",
                    styled("#" + ranking, YELLOW)).formatted(GRAY));
            return;
        }

        RootText translatedExtra = extra.translateFor(player);
        player.sendMessage(translations.translateText(player, "ap2.you_placed_value",
                styled("#" + ranking, YELLOW),
                translatedExtra.formatted(YELLOW)).formatted(GRAY));
    }
}
