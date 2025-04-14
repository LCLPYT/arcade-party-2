package work.lclpnet.ap2.impl.game;

import it.unimi.dsi.fastutil.objects.ObjectIntPair;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import work.lclpnet.ap2.api.game.data.DataEntry;
import work.lclpnet.ap2.api.game.data.PlayerSubjectRefFactory;
import work.lclpnet.ap2.api.game.data.SubjectRef;
import work.lclpnet.ap2.base.ApConstants;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.RootText;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static net.minecraft.util.Formatting.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class ResultAnnouncement<Ref extends SubjectRef> {

    private final Translations translations;
    private final PlayerSubjectRefFactory<Ref> refs;
    private final List<ObjectIntPair<Ref>> order;
    private final Map<Ref, Integer> placement;
    private final HashMap<Ref, DataEntry<Ref>> entryByRef;

    public ResultAnnouncement(Translations translations, PlayerSubjectRefFactory<Ref> refs,
                              List<ObjectIntPair<Ref>> order, Function<Ref, Optional<DataEntry<Ref>>> entryGetter) {
        this.translations = translations;
        this.refs = refs;

        this.order = order;
        this.placement = new HashMap<>();
        this.entryByRef = new HashMap<>();

        for (ObjectIntPair<Ref> rankEntry : order) {
            Ref ref = rankEntry.left();
            DataEntry<Ref> entry = entryGetter.apply(ref).orElse(null);

            if (entry == null) continue;

            placement.put(ref, rankEntry.rightInt());
            entryByRef.put(ref, entry);
        }
    }

    public void sendTop(int amount, ServerPlayerEntity player) {
        String results = translations.translate(player, "ap2.results");
        int len = results.length() + 2;

        var resultsText = Text.literal(results).formatted(GREEN);

        var sep = Text.literal(ApConstants.SEPARATOR).formatted(DARK_GREEN, STRIKETHROUGH, BOLD);
        int sepLength = ApConstants.SEPARATOR.length();
        var sepSm = Text.literal("-".repeat(sepLength)).formatted(DARK_GRAY, STRIKETHROUGH);

        sendUpperSeparator(player, len, sepLength, resultsText);

        if (order.isEmpty()) {
            player.sendMessage(translations.translateText(player, "ap2.no_results").formatted(GRAY));
        } else {
            sendRankList(amount, player);
            sendOwnScoreIfExists(player, sepSm);
        }

        player.sendMessage(sep);
    }

    private void sendOwnScoreIfExists(ServerPlayerEntity player, MutableText sepSm) {
        var ownRef = refs.create(player);

        if (ownRef == null || !placement.containsKey(ownRef)) return;

        int playerIndex = placement.get(ownRef);
        DataEntry<Ref> entry = entryByRef.get(ownRef);

        sendOwnScore(player, entry, playerIndex, sepSm);
    }

    private void sendRankList(int amount, ServerPlayerEntity player) {

        for (int i = 0; i < amount; i++) {
            if (order.size() <= i) break;

            ObjectIntPair<Ref> rankEntry = order.get(i);
            Ref subject = rankEntry.left();

            var entry = entryByRef.getOrDefault(subject, null);
            var text = entry != null ? entry.toText(translations) : null;

            Text subjectName = subject.getNameFor(player);

            if (subjectName.getStyle().getColor() == null) {
                subjectName = subjectName.copy().formatted(GRAY);
            }

            MutableText msg = Text.literal("#%s ".formatted(rankEntry.rightInt())).formatted(YELLOW)
                    .append(subjectName);

            if (text != null) {
                msg.append(" ").append(text.translateFor(player));
            }

            player.sendMessage(msg);
        }
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
