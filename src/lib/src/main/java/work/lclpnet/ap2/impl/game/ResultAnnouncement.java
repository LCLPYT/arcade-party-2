package work.lclpnet.ap2.impl.game;

import it.unimi.dsi.fastutil.objects.ObjectIntPair;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.game.data.DataEntry;
import work.lclpnet.ap2.api.game.data.PlayerSubjectRefFactory;
import work.lclpnet.ap2.api.game.data.SubjectRef;
import work.lclpnet.ap2.util.FontService;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.RootText;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static java.lang.Math.round;
import static net.minecraft.ChatFormatting.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class ResultAnnouncement<Ref extends SubjectRef> {

    private final Translations translations;
    private final FontService font;
    private final PlayerSubjectRefFactory<Ref> refs;
    private final List<ObjectIntPair<Ref>> order;
    private final Map<Ref, Integer> placement;
    private final HashMap<Ref, DataEntry<Ref>> entryByRef;

    public ResultAnnouncement(Translations translations, FontService font, PlayerSubjectRefFactory<Ref> refs,
                              List<ObjectIntPair<Ref>> order, Function<Ref, Optional<DataEntry<Ref>>> entryGetter) {
        this.translations = translations;
        this.font = font;
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

    public void sendTop(int amount, ServerPlayer player) {
        sendTop(amount, player, null);
    }

    public void sendTop(int amount, ServerPlayer player, @Nullable Component actionText) {
        String results = translations.translate(player, "ap2.results");

        var resultsText = Component.literal(results).withStyle(GREEN, BOLD);

        var sep = Component.literal(ApConstants.SEPARATOR).withStyle(DARK_GREEN, STRIKETHROUGH, BOLD);
        int sepLength = ApConstants.SEPARATOR.length();
        var sepSm = Component.literal("-".repeat(sepLength)).withStyle(DARK_GRAY, STRIKETHROUGH);

        sendSeparatorWithText(player, resultsText);

        if (order.isEmpty()) {
            player.sendSystemMessage(translations.translateText(player, "ap2.no_results").formatted(GRAY));
        } else {
            sendRankList(amount, player);
            sendOwnScoreIfExists(player, sepSm);
        }

        if (actionText != null) {
            sendSeparatorWithText(player, actionText);
        } else {
            player.sendSystemMessage(sep);
        }
    }

    private void sendOwnScoreIfExists(ServerPlayer player, MutableComponent sepSm) {
        var ownRef = refs.create(player);

        if (ownRef == null || !placement.containsKey(ownRef)) return;

        int playerIndex = placement.get(ownRef);
        DataEntry<Ref> entry = entryByRef.get(ownRef);

        sendOwnScore(player, entry, playerIndex, sepSm);
    }

    private void sendRankList(int amount, ServerPlayer player) {
        for (int i = 0; i < amount; i++) {
            if (order.size() <= i) break;

            ObjectIntPair<Ref> rankEntry = order.get(i);
            Ref subject = rankEntry.left();

            var entry = entryByRef.getOrDefault(subject, null);
            var text = entry != null ? entry.toText(translations) : null;

            Component subjectName = subject.getNameFor(player);

            if (subjectName.getStyle().getColor() == null) {
                subjectName = subjectName.copy().withStyle(GRAY);
            }

            MutableComponent msg = Component.literal("#%s ".formatted(rankEntry.rightInt())).withStyle(YELLOW)
                    .append(subjectName);

            if (text != null) {
                msg.append(" ").append(text.translateFor(player));
            }

            player.sendSystemMessage(msg);
        }
    }

    private void sendSeparatorWithText(ServerPlayer player, Component label) {
        // the full separator and its '=' padding are bold; center the bracketed label by pixel width,
        // since the default Minecraft font is not monospaced (see FontService)
        float target = font.width(ApConstants.SEPARATOR, true);
        float eq = font.advance('=', true);
        float brackets = font.advance('[', true) + font.advance(']', true);
        float labelWidth = font.width(label) + brackets;

        float side = (target - labelWidth) / 2f;

        if (side < eq) {
            player.sendSystemMessage(label);
            return;
        }

        int left = round(side / eq);
        int right = Math.max(0, round((target - labelWidth - left * eq) / eq));

        var msg = Component.empty()
                .append(Component.literal("=".repeat(left)).withStyle(DARK_GREEN, STRIKETHROUGH, BOLD))
                .append(Component.literal("[").withStyle(DARK_GREEN, BOLD))
                .append(label)
                .append(Component.literal("]").withStyle(DARK_GREEN, BOLD))
                .append(Component.literal("=".repeat(right)).withStyle(DARK_GREEN, STRIKETHROUGH, BOLD));

        player.sendSystemMessage(msg);
    }

    private void sendOwnScore(ServerPlayer player, DataEntry<Ref> entry, int ranking, MutableComponent sepSm) {
        player.sendSystemMessage(sepSm);

        var extra = entry.toText(translations);

        if (extra == null) {
            player.sendSystemMessage(translations.translateText(player, "ap2.you_placed",
                    styled("#" + ranking, YELLOW)).formatted(GRAY));
            return;
        }

        RootText translatedExtra = extra.translateFor(player);
        player.sendSystemMessage(translations.translateText(player, "ap2.you_placed_value",
                styled("#" + ranking, YELLOW),
                translatedExtra.formatted(YELLOW)).formatted(GRAY));
    }
}
