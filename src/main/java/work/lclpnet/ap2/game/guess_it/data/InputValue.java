package work.lclpnet.ap2.game.guess_it.data;

import it.unimi.dsi.fastutil.Pair;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.text.TranslatedText;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static net.minecraft.util.Formatting.RED;
import static net.minecraft.util.Formatting.YELLOW;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

@SuppressWarnings("UnusedReturnValue")
public class InputValue {

    private final List<InputRule> rules = new ArrayList<>();
    private boolean once = false;

    public InputValue validate(Function<String, Optional<String>> validator, Function<String, TranslatedText> errorMessage) {
        rules.add(new InputRule(validator, errorMessage));
        return this;
    }

    public InputValue validateInt(TranslationService translations) {
        return validate(InputValue::intValue, input ->
                translations.translateText("game.ap2.guess_it.input.int", styled(input, YELLOW)).formatted(RED));
    }

    public InputValue onlyOnce() {
        once = true;
        return this;
    }

    public Pair<String, @Nullable TranslatedText> validate(String input) {
        for (InputRule rule : rules) {
            var res = rule.validator().apply(input);

            if (res.isEmpty()) {
                return Pair.of(input, rule.errorMessage.apply(input));
            }

            input = res.get();
        }

        return Pair.of(input, null);
    }

    public boolean isOnce() {
        return once;
    }

    public static Optional<String> intValue(String s) {
        try {
            int i = Integer.parseInt(s, 10);
            return Optional.of(String.valueOf(i));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private record InputRule(Function<String, Optional<String>> validator, Function<String, TranslatedText> errorMessage) {}
}
