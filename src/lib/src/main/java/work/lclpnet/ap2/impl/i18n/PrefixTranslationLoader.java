package work.lclpnet.ap2.impl.i18n;

import work.lclpnet.translations.loader.TranslationLoader;
import work.lclpnet.translations.model.Language;
import work.lclpnet.translations.model.LanguageCollection;
import work.lclpnet.translations.model.MutableLanguage;
import work.lclpnet.translations.model.StaticLanguageCollection;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link TranslationLoader} that wraps another loader and prepends a fixed prefix to every
 * translation key it provides.
 *
 * <p>This allows translation files to omit a common prefix. Keys are joined with the prefix using
 * a dot, except for the {@link #BASE_KEY} key which maps to the bare prefix.</p>
 */
public class PrefixTranslationLoader implements TranslationLoader {

    /**
     * The relative key that maps to the bare prefix (without a trailing dot).
     */
    public static final String BASE_KEY = "name";

    private final TranslationLoader delegate;
    private final String prefix;

    public PrefixTranslationLoader(TranslationLoader delegate, String prefix) {
        this.delegate = delegate;
        this.prefix = prefix;
    }

    @Override
    public CompletableFuture<? extends LanguageCollection> load() {
        return delegate.load().thenApply(this::applyPrefix);
    }

    private LanguageCollection applyPrefix(LanguageCollection collection) {
        Map<String, Language> languages = new HashMap<>();

        for (String languageName : collection.keys()) {
            Language language = collection.get(languageName);
            if (language == null) continue;

            MutableLanguage prefixed = new MutableLanguage();

            for (String key : language.keys()) {
                prefixed.add(applyPrefix(key), language.get(key));
            }

            languages.put(languageName, prefixed);
        }

        return new StaticLanguageCollection(languages);
    }

    private String applyPrefix(String key) {
        if (BASE_KEY.equals(key)) {
            return prefix;
        }

        return prefix + "." + key;
    }
}
