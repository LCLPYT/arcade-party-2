package work.lclpnet.ap2.game.guess_it.data;

import it.unimi.dsi.fastutil.Pair;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.TranslatedText;

import java.util.Optional;

import static net.minecraft.util.Formatting.RED;
import static net.minecraft.util.Formatting.YELLOW;
import static work.lclpnet.kibu.translate.text.FormatWrapper.styled;

public class OptionValue {

    private final Translations translations;
    private final int options;

    public OptionValue(Translations translations, int options) {
        if (options < 2) {
            throw new IllegalArgumentException("There must be at least two options");
        }

        this.translations = translations;
        this.options = options;
    }

    public Pair<String, @Nullable TranslatedText> validate(String input) {
        var option = parseOption(input);

        if (option.isEmpty()) {
            char from = 'A';
            char to = (char) ('A' + options - 1);

            var err = translations.translateText("game.ap2.guess_it.input.option",
                            styled(input, YELLOW), styled(from, YELLOW), styled(to, YELLOW))
                    .formatted(RED);

            return Pair.of(null, err);
        }

        return Pair.of(option.get(), null);
    }

    private Optional<String> parseOption(String input) {
        input = input.trim();

        if (input.endsWith(")")) {
            input = input.substring(0, input.length() - 1);
        }

        if (input.length() == 1) {
            char c = Character.toUpperCase(input.charAt(0));
            int index = c - 'A';

            if (index >= 0 && index < options) {
                return Optional.of(String.valueOf(c));
            }
        }

        int num;

        try {
            num = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }

        num -= 1;

        if (num >= 0 && num < options) {
            char letter = (char) ('A' + num);
            return Optional.of(String.valueOf(letter));
        }

        return Optional.empty();
    }
}
