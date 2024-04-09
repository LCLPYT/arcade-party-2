package work.lclpnet.ap2.game.guess_it.data;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.kibu.translate.text.TranslatedText;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

import static net.minecraft.util.Formatting.RED;
import static net.minecraft.util.Formatting.YELLOW;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

@SuppressWarnings("UnusedReturnValue")
public class InputValue {

    private final List<InputRule> rules = new ArrayList<>();
    private boolean once = false;

    public InputValue validate(InputParser validator, Function<String, TranslatedText> errorMessage) {
        rules.add(new InputRule(validator, errorMessage));
        return this;
    }

    public InputValue validateInt(TranslationService translations) {
        return validate(InputValue::intValue, input ->
                translations.translateText("game.ap2.guess_it.input.int", styled(input, YELLOW)).formatted(RED));
    }

    public InputValue validateFloat(TranslationService translations, int precision) {
        return validate((input, player) -> floatValue(input, player, translations, precision), input ->
                translations.translateText("game.ap2.guess_it.input.float", styled(input, YELLOW)).formatted(RED));
    }

    public InputValue onlyOnce() {
        once = true;
        return this;
    }

    public Pair<String, @Nullable TranslatedText> validate(String input, ServerPlayerEntity player) {
        for (InputRule rule : rules) {
            var res = rule.parser().parse(input, player);

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

    private static Optional<String> floatValue(String s, ServerPlayerEntity player, TranslationService translations, int precision) {
        Locale locale = translations.getLocale(player);
        NumberFormat format = NumberFormat.getInstance(locale);

        float f;

        try {
            f = format.parse(s).floatValue();
        } catch (ParseException e) {
            return Optional.empty();
        }

        String fmt = "%." + Math.max(0, Math.min(7, precision)) + "f";
        String str = String.format(locale, fmt, f);

        return Optional.of(str);
    }

    public static Optional<String> intValue(String s, ServerPlayerEntity player) {
        try {
            int i = Integer.parseInt(s, 10);
            return Optional.of(String.valueOf(i));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public interface InputParser {
        Optional<String> parse(String input, ServerPlayerEntity player);
    }

    private record InputRule(InputParser parser, Function<String, TranslatedText> errorMessage) {}
}
