package work.lclpnet.ap2.game.guess_it.data;

import it.unimi.dsi.fastutil.Pair;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.kibu.hook.ServerMessageHooks;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.text.TranslatedText;

import static net.minecraft.util.Formatting.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class InputManager implements InputInterface {

    private final PlayerChoices choices;
    private final TranslationService translations;
    private final Participants participants;
    private final ServerWorld world;
    private InputValue inputValue = null;
    private OptionValue optionValue = null;
    private boolean locked = false;

    public InputManager(PlayerChoices choices, TranslationService translations, Participants participants, ServerWorld world) {
        this.choices = choices;
        this.translations = translations;
        this.participants = participants;
        this.world = world;
    }

    public void init(HookRegistrar hooks) {
        hooks.registerHook(ServerMessageHooks.ALLOW_CHAT_MESSAGE, (message, sender, params) -> {
            onChat(message, sender, params);
            return false;
        });
    }

    private void onChat(SignedMessage signedMessage, ServerPlayerEntity player, MessageType.Parameters parameters) {
        String input = signedMessage.signedBody().content();
        input(player, input);
    }

    public void input(ServerPlayerEntity player, String input) {
        if (!participants.isParticipating(player) || locked) return;

        Pair<String, @Nullable TranslatedText> res;

        if (inputValue != null) {
            if (inputValue.isOnce() && hasAnswered(player)) {
                var msg = translations.translateText(player, "game.ap2.guess_it.already_answered").formatted(RED);
                player.sendMessage(msg);
                player.playSound(SoundEvents.ENTITY_BLAZE_HURT, SoundCategory.PLAYERS, 0.5f, 0f);
                return;
            }

            res = inputValue.validate(input, player);
        } else if (optionValue != null) {
            res = optionValue.validate(input);
        } else {
            return;
        }

        TranslatedText err = res.right();

        if (err != null) {
            player.sendMessage(err.translateFor(player));
            return;
        }

        String transformedInput = res.left();
        onAnswer(player, transformedInput);
    }

    private void onAnswer(ServerPlayerEntity player, String input) {
        choices.set(player, input);

        var msg = translations.translateText(player, "game.ap2.guess_it.guessed", styled(input, YELLOW)).formatted(GREEN);
        player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 0.75f, 1.5f);

        player.sendMessage(msg);
    }

    private boolean hasAnswered(ServerPlayerEntity player) {
        return choices.getInt(player).isPresent();
    }

    @Override
    public InputValue expectInput() {
        reset();

        inputValue = new InputValue();

        return inputValue;
    }

    @Override
    public void expectSelection(Text... options) {
        reset();
        sendOptions(options);

        optionValue = new OptionValue(translations, options.length);
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

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public void reset() {
        inputValue = null;
        optionValue = null;
        choices.clear();
        locked = false;
    }
}

