package work.lclpnet.ap2.game.guess_it.data;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.api.base.Participants;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.ServerMessageHooks;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.TranslatedText;

import static net.minecraft.util.Formatting.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class InputManager implements InputInterface {

    private final PlayerChoices choices;
    private final Translations translations;
    private final Participants participants;
    private final ChallengeMessenger messenger;
    private InputValue inputValue = null;
    private OptionValue optionValue = null;
    private boolean locked = false;

    public InputManager(PlayerChoices choices, Translations translations, Participants participants, ChallengeMessenger messenger) {
        this.choices = choices;
        this.translations = translations;
        this.participants = participants;
        this.messenger = messenger;
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
                player.playSoundToPlayer(SoundEvents.ENTITY_BLAZE_HURT, SoundCategory.PLAYERS, 0.5f, 0f);
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
        player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 0.75f, 1.5f);

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
        messenger.options(options);

        optionValue = new OptionValue(translations, options.length);
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

