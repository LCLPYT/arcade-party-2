package work.lclpnet.ap2.game.guess_it.data;

import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import work.lclpnet.kibu.hook.ServerMessageHooks;
import work.lclpnet.kibu.plugin.hook.HookRegistrar;
import work.lclpnet.kibu.translate.TranslationService;

import static net.minecraft.util.Formatting.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class InputManager implements InputInterface {

    private final PlayerChoices choices;
    private final TranslationService translations;
    private InputValue inputValue = null;

    public InputManager(PlayerChoices choices, TranslationService translations) {
        this.choices = choices;
        this.translations = translations;
    }

    public void init(HookRegistrar hooks) {
        hooks.registerHook(ServerMessageHooks.ALLOW_CHAT_MESSAGE, (message, sender, params) -> {
            onChat(message, sender, params);
            return false;
        });
    }

    private void onChat(SignedMessage signedMessage, ServerPlayerEntity player, MessageType.Parameters parameters) {
        if (inputValue != null) {
            if (inputValue.isOnce() && hasAnswered(player)) {
                var msg = translations.translateText(player, "game.ap2.guess_it.already_answered").formatted(RED);
                player.sendMessage(msg);
                player.playSound(SoundEvents.ENTITY_BLAZE_HURT, SoundCategory.PLAYERS, 0.5f, 0f);
                return;
            }

            String content = signedMessage.signedBody().content();

            inputValue.validate(content).ifPresentOrElse(
                    errMsg -> player.sendMessage(errMsg.translateFor(player)),
                    ()     -> onAnswer(player, content));
        }
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
    public void expectSelection(String... options) {
        reset();
        // TODO implement
    }

    public void reset() {
        inputValue = null;
        choices.clear();
    }
}

