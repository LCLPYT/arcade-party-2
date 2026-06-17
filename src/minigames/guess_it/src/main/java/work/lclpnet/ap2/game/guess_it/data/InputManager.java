package work.lclpnet.ap2.game.guess_it.data;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.ap2.game.player.Participants;
import work.lclpnet.kibu.access.entity.ServerPlayerAccess;
import work.lclpnet.kibu.hook.HookRegistrar;
import work.lclpnet.kibu.hook.ServerMessageHooks;
import work.lclpnet.kibu.hook.network.CustomClickActionCallback;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.TranslatedText;

import static net.minecraft.ChatFormatting.*;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class InputManager implements InputInterface {

    private final PlayerChoices choices;
    private final Translations translations;
    private final Participants participants;
    private final ChallengeMessenger messenger;
    private final Identifier answerId;
    private InputValue inputValue = null;
    private OptionValue optionValue = null;
    private boolean locked = false;

    public InputManager(PlayerChoices choices, Translations translations, Participants participants, ChallengeMessenger messenger, Identifier answerId) {
        this.choices = choices;
        this.translations = translations;
        this.participants = participants;
        this.messenger = messenger;
        this.answerId = answerId;
    }

    public void init(HookRegistrar hooks) {
        ServerMessageHooks.ALLOW_CHAT_MESSAGE.registerWith(hooks, (message, sender, params) -> {
            onChat(message, sender, params);
            return false;
        });

        CustomClickActionCallback.HOOK.registerWith(hooks, (player, id, payload) -> {
            if (!id.equals(answerId)) return;

            if (payload.orElse(null) instanceof StringTag(String value)) {
                input(player, value);
            }
        });
    }

    private void onChat(PlayerChatMessage signedMessage, ServerPlayer player, ChatType.Bound parameters) {
        String input = signedMessage.signedBody().content();
        input(player, input);
    }

    public void input(ServerPlayer player, String input) {
        if (!participants.isParticipating(player) || locked) return;

        Pair<String, @Nullable TranslatedText> res;

        if (inputValue != null) {
            if (inputValue.isOnce() && hasAnswered(player)) {
                var msg = translations.translateText(player, "already_answered").formatted(RED);
                player.sendSystemMessage(msg);
                ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.BLAZE_HURT, SoundSource.PLAYERS, 0.5f, 0f);
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
            player.sendSystemMessage(err.translateFor(player));
            return;
        }

        String transformedInput = res.left();
        onAnswer(player, transformedInput);
    }

    private void onAnswer(ServerPlayer player, String input) {
        choices.set(player, input);

        var msg = translations.translateText(player, "guessed", styled(input, YELLOW)).formatted(GREEN);
        ServerPlayerAccess.playSoundToPlayer(player, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.75f, 1.5f);

        player.sendSystemMessage(msg);
    }

    private boolean hasAnswered(ServerPlayer player) {
        return choices.getInt(player).isPresent();
    }

    @Override
    public InputValue expectInput() {
        reset();

        inputValue = new InputValue();

        return inputValue;
    }

    @Override
    public void expectSelection(Component... options) {
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

