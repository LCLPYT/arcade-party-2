package work.lclpnet.ap2.game.guess_it.data;

import it.unimi.dsi.fastutil.Pair;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
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
        if (!participants.isParticipating(player)) return;

        Pair<String, @Nullable TranslatedText> res;

        if (inputValue != null) {
            if (inputValue.isOnce() && hasAnswered(player)) {
                var msg = translations.translateText(player, "game.ap2.guess_it.already_answered").formatted(RED);
                player.sendMessage(msg);
                player.playSound(SoundEvents.ENTITY_BLAZE_HURT, SoundCategory.PLAYERS, 0.5f, 0f);
                return;
            }

            String content = signedMessage.signedBody().content();
            res = inputValue.validate(content);
        } else if (optionValue != null) {
            String content = signedMessage.signedBody().content();
            res = optionValue.validate(content);
        } else {
            return;
        }

        TranslatedText err = res.right();

        if (err != null) {
            player.sendMessage(err.translateFor(player));
            return;
        }

        String input = res.left();
        onAnswer(player, input);
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
            Text msg = Text.literal(letter + ") ").formatted(YELLOW)
                    .append(option.copy().formatted(AQUA));

            for (ServerPlayerEntity player : players) {
                player.sendMessage(msg);
            }

            letter++;
        }
    }

    public void reset() {
        inputValue = null;
        optionValue = null;
        choices.clear();
    }
}

