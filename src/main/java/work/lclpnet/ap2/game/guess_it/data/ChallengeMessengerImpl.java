package work.lclpnet.ap2.game.guess_it.data;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.TranslatedText;

import static net.minecraft.util.Formatting.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class ChallengeMessengerImpl implements ChallengeMessenger {

    private final ServerWorld world;
    private final Translations translations;
    private TranslatedText task = null;
    private Text[] options = null;

    public ChallengeMessengerImpl(ServerWorld world, Translations translations) {
        this.world = world;
        this.translations = translations;
    }

    @Override
    public void task(TranslatedText task) {
        this.task = task;
    }

    @Override
    public void options(Text[] options) {
        this.options = options;
    }

    public void send() {
        if (task == null) return;

        var msg = task.formatted(Formatting.DARK_GREEN, BOLD);

        for (ServerPlayerEntity player : PlayerLookup.world(world)) {
            for (int i = 0; i < 20; i++) {
                player.sendMessage(Text.empty());
            }

            player.sendMessage(msg.translateFor(player));
        }

        if (options != null) {
            sendOptions(options);
        }
    }

    public void reset() {
        task = null;
        options = null;
    }

    private void sendOptions(Text[] options) {
        var players = PlayerLookup.world(world);
        char letter = 'A';

        for (Text option : options) {
            ClickEvent clickEvent = new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/answer " + letter);

            for (ServerPlayerEntity player : players) {
                var hoverMsg = translations.translateText(player, "game.ap2.guess_it.hover_option", styled(letter, YELLOW))
                        .formatted(GREEN);

                HoverEvent hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverMsg);

                Text msg = Text.literal(letter + ") ").formatted(YELLOW)
                        .append(option.copy().formatted(AQUA))
                        .styled(style -> style.withClickEvent(clickEvent).withHoverEvent(hoverEvent));

                player.sendMessage(msg);
            }

            letter++;
        }
    }
}
