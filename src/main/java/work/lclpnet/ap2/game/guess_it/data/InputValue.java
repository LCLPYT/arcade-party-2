package work.lclpnet.ap2.game.guess_it.data;

import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.text.TranslatedText;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import static net.minecraft.util.Formatting.RED;
import static net.minecraft.util.Formatting.YELLOW;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

@SuppressWarnings("UnusedReturnValue")
public class InputValue {

    private final List<InputRule> rules = new ArrayList<>();
    private boolean once = true;

    public InputValue validate(Predicate<String> validator, Function<String, TranslatedText> errorMessage) {
        rules.add(new InputRule(validator, errorMessage));
        return this;
    }

    public InputValue validateInt(TranslationService translations) {
        return validate(InputValue::isInt, input ->
                translations.translateText("game.ap2.guess_it.input.int", styled(input, YELLOW)).formatted(RED));
    }

    public InputValue onlyOnce() {
        once = true;
        return this;
    }

    public Optional<TranslatedText> validate(String input) {
        for (InputRule rule : rules) {
            if (rule.validator().test(input)) continue;

            return Optional.of(rule.errorMessage.apply(input));
        }

        return Optional.empty();
    }

    public boolean isOnce() {
        return once;
    }

    public static boolean isInt(String s) {
        try {
            Integer.parseInt(s, 10);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private record InputRule(Predicate<String> validator, Function<String, TranslatedText> errorMessage) {}
}
