package work.lclpnet.ap2.game.guess_it.data;

import it.unimi.dsi.fastutil.Pair;
import lombok.Getter;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.TranslatedText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

import static java.lang.Math.clamp;
import static net.minecraft.ChatFormatting.RED;
import static net.minecraft.ChatFormatting.YELLOW;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

@SuppressWarnings("UnusedReturnValue")
public class InputValue {

    private final List<InputRule> rules = new ArrayList<>();
    @Getter
    private boolean once = false;

    public InputValue validate(InputParser validator, Function<String, TranslatedText> errorMessage) {
        rules.add(new InputRule(validator, errorMessage));
        return this;
    }

    public InputValue validateInt(Translations translations) {
        return validate(InputValue::intValue, input ->
                translations.translateText("game.ap2.guess_it.input.int", styled(input, YELLOW)).formatted(RED));
    }

    public InputValue validateFloat(Translations translations, int precision) {
        return validate((input, player) -> floatValue(input, player, translations, precision), input ->
                translations.translateText("game.ap2.guess_it.input.float", styled(input, YELLOW)).formatted(RED));
    }

    public InputValue onlyOnce() {
        once = true;
        return this;
    }

    public Pair<String, @Nullable TranslatedText> validate(String input, ServerPlayer player) {
        for (InputRule rule : rules) {
            var res = rule.parser().parse(input, player);

            if (res.isEmpty()) {
                return Pair.of(input, rule.errorMessage.apply(input));
            }

            input = res.get();
        }

        return Pair.of(input, null);
    }

    private static Optional<String> floatValue(String s, ServerPlayer player, Translations translations, int precision) {
        s = s.replace(',', '.');

        float f;

        try {
            f = Float.parseFloat(s);
        } catch (NumberFormatException _) {
            return Optional.empty();
        }

        String fmt = "%." + clamp(precision, 0, 7) + "f";
        Locale locale = translations.getLocale(player);
        String str = String.format(locale, fmt, f);

        return Optional.of(str);
    }

    public static Optional<String> intValue(String s, ServerPlayer player) {
        try {
            int i = Integer.parseInt(s, 10);
            return Optional.of(String.valueOf(i));
        } catch (NumberFormatException _) {
            return Optional.empty();
        }
    }

    public interface InputParser {
        Optional<String> parse(String input, ServerPlayer player);
    }

    private record InputRule(InputParser parser, Function<String, TranslatedText> errorMessage) {}
}
