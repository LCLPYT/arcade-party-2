package work.lclpnet.ap2.impl.game;

import it.unimi.dsi.fastutil.objects.ObjectIntPair;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.objects.ObjectInfo;
import net.minecraft.network.chat.contents.objects.PlayerSprite;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.ApConstants;
import work.lclpnet.ap2.api.game.data.DataEntry;
import work.lclpnet.ap2.api.game.data.PlayerSubjectRefFactory;
import work.lclpnet.ap2.api.game.data.SubjectRef;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.RootText;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static net.minecraft.ChatFormatting.*;
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

    public void sendTop(int amount, ServerPlayer player) {
        sendTop(amount, player, null);
    }

    public void sendTop(int amount, ServerPlayer player, @Nullable Component actionText) {
        String results = translations.translate(player, "ap2.results");

        var resultsText = Component.literal(results).withStyle(GREEN, BOLD);

        var sep = Component.literal(ApConstants.SEPARATOR).withStyle(DARK_GREEN, STRIKETHROUGH, BOLD);
        int sepLength = ApConstants.SEPARATOR.length();
        var sepSm = Component.literal("-".repeat(sepLength)).withStyle(DARK_GRAY, STRIKETHROUGH);

        sendSeparatorWithText(player, resultsText, sepLength);

        if (order.isEmpty()) {
            player.sendSystemMessage(translations.translateText(player, "ap2.no_results").formatted(GRAY));
        } else {
            sendRankList(amount, player);
            sendOwnScoreIfExists(player, sepSm);
        }

        if (actionText != null) {
            sendSeparatorWithText(player, actionText, sepLength);
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

    private void sendSeparatorWithText(ServerPlayer player, Component label, int sepLength) {
        int len = label.getString().length() + 2;

        if (len - 1 >= sepLength) {
            player.sendSystemMessage(label);
            return;
        }

        int times = (sepLength - len) / 2;
        String sepShort = "=".repeat(times);

        var msg = Component.empty()
                .append(Component.literal(sepShort).withStyle(DARK_GREEN, STRIKETHROUGH, BOLD))
                .append(Component.literal("[").withStyle(DARK_GREEN, BOLD))
                .append(label)
                .append(Component.literal("]").withStyle(DARK_GREEN, BOLD))
                .append(Component.literal(sepShort + (sepLength - 2 * times - len > 0 ? "=" : ""))
                        .withStyle(DARK_GREEN, STRIKETHROUGH, BOLD));

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
