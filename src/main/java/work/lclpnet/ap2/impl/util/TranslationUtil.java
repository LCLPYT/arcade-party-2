package work.lclpnet.ap2.impl.util;

import net.minecraft.text.Style;
import net.minecraft.text.Text;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.text.RootText;
import work.lclpnet.kibu.translate.text.TextTranslatable;
import work.lclpnet.kibu.translate.text.TranslatedText;

import java.util.function.UnaryOperator;

import static net.minecraft.text.Text.literal;

public class TranslationUtil {

    private TranslationUtil() {}

    public static TextTranslatable quote(TextTranslatable other) {
        return language -> {
            Text inner = other.translateTo(language);
            Style style = inner.getStyle();

            return switch (language) {
                case "de_de" -> literal("„").append(inner).append("“").setStyle(style);
                case "en_gb" -> literal("'").append(inner).append("'").setStyle(style);
                case "ja_jp" -> literal("「").append(inner).append("」").setStyle(style);
                default      -> literal("\"").append(inner).append("\"").setStyle(style);
            };
        };
    }

    public static TextTranslatable transform(TextTranslatable translatable, UnaryOperator<Text> action) {
        return language -> action.apply(translatable.translateTo(language));
    }

    public static TranslatedText asTranslatedText(TextTranslatable translatable, Translations translations) {
        return TranslatedText.create(
                language -> RootText.create().append(translatable.translateTo(language)),
                translations::getLanguage
        );
    }

    public static TranslatedText transformText(TextTranslatable translatable, UnaryOperator<Text> action, Translations translations) {
        return asTranslatedText(transform(translatable, action), translations);
    }
}
