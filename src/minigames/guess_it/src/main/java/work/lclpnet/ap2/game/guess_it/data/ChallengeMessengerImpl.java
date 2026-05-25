package work.lclpnet.ap2.game.guess_it.data;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.TranslatedText;

import java.util.Optional;

import static net.minecraft.ChatFormatting.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class ChallengeMessengerImpl implements ChallengeMessenger {

    private final ServerLevel world;
    private final Translations translations;
    private final Identifier answerId;
    private TranslatedText task = null;
    private Component[] options = null;

    public ChallengeMessengerImpl(ServerLevel world, Translations translations, Identifier answerId) {
        this.world = world;
        this.translations = translations;
        this.answerId = answerId;
    }

    @Override
    public void task(TranslatedText task) {
        this.task = task;
    }

    @Override
    public void options(Component[] options) {
        this.options = options;
    }

    public void send() {
        if (task == null) return;

        var msg = task.formatted(ChatFormatting.DARK_GREEN, BOLD);

        for (ServerPlayer player : PlayerLookup.level(world)) {
            for (int i = 0; i < 20; i++) {
                player.sendSystemMessage(Component.empty());
            }

            player.sendSystemMessage(msg.translateFor(player));
        }

        if (options != null) {
            sendOptions(options);
        }
    }

    public void reset() {
        task = null;
        options = null;
    }

    private void sendOptions(Component[] options) {
        var players = PlayerLookup.level(world);
        char letter = 'A';

        for (Component option : options) {
            StringTag payload = StringTag.valueOf(String.valueOf(letter));
            ClickEvent clickEvent = new ClickEvent.Custom(answerId, Optional.of(payload));

            for (ServerPlayer player : players) {
                var hoverMsg = translations.translateText(player, "game.ap2.guess_it.hover_option", styled(letter, YELLOW))
                        .formatted(GREEN);

                HoverEvent hoverEvent = new HoverEvent.ShowText(hoverMsg);

                Component msg = Component.literal(letter + ") ").withStyle(YELLOW)
                        .append(option.copy().withStyle(AQUA))
                        .withStyle(style -> style.withClickEvent(clickEvent).withHoverEvent(hoverEvent));

                player.sendSystemMessage(msg);
            }

            letter++;
        }
    }
}
