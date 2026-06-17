package work.lclpnet.ap2.impl.i18n;

import org.jetbrains.annotations.NotNull;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.kibu.translate.util.ScopedTranslator;
import work.lclpnet.translations.Translator;

/**
 * A {@link Translator} that scopes lookups to a single mini-game by prepending its translation key
 * prefix, mirroring {@link PrefixTranslationLoader}.
 *
 * <p>Relative keys are resolved against the prefix ({@code prefix + "." + key}), with the special
 * {@link PrefixTranslationLoader#BASE_KEY} mapping to the bare prefix. If no prefixed translation
 * exists, the lookup falls back to the raw key. This allows mini-game code to use relative keys while
 * still resolving shared keys (e.g. {@code ap2.*} or vanilla {@code death.*}) that are not scoped to a
 * specific game.</p>
 */
public class GameScopedTranslator extends ScopedTranslator {

    public GameScopedTranslator(Translator parent, String titleKey) {
        super(parent, titleKey);
    }

    @Override
    public String prefixed(String key) {
        if (PrefixTranslationLoader.BASE_KEY.equals(key)) {
            return prefix;
        }

        return prefix + "." + key;
    }

    @Override
    public @NotNull String translate(String locale, String key) {
        String scoped = prefixed(key);

        if (parent.hasTranslation(locale, scoped)) {
            return parent.translate(locale, scoped);
        }

        return parent.translate(locale, key);
    }

    @Override
    public boolean hasTranslation(String locale, String key) {
        return parent.hasTranslation(locale, prefixed(key)) || parent.hasTranslation(locale, key);
    }

    /**
     * Create a {@link Translations} view of the given parent that scopes lookups to a mini-game.
     *
     * @param parent   The parent translations to delegate to.
     * @param titleKey The mini-game title key used as the prefix (e.g. {@code game.ap2.foo}).
     * @return A scoped {@link Translations} instance with fallback to unscoped keys.
     */
    public static Translations scope(Translations parent, String titleKey) {
        return new Translations(new GameScopedTranslator(parent.getTranslator(), titleKey));
    }
}
