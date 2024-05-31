package work.lclpnet.ap2.impl.util;

import net.minecraft.text.Style;
import net.minecraft.text.Text;
import work.lclpnet.kibu.translate.text.TextTranslatable;

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
}
