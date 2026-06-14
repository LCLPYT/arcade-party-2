package work.lclpnet.ap2.impl.util.scoreboard;

import net.minecraft.network.chat.numbers.NumberFormat;

/**
 * A {@link NumberFormat} that may differ per language, resolved for a viewer's language.
 */
@FunctionalInterface
public interface TranslatedNumberFormat {

    NumberFormat translateTo(String language);

    static TranslatedNumberFormat constant(NumberFormat format) {
        return language -> format;
    }
}
