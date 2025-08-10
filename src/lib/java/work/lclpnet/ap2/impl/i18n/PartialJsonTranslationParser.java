package work.lclpnet.ap2.impl.i18n;

import org.json.JSONObject;
import org.json.JSONTokener;
import work.lclpnet.translations.model.LanguageCollection;
import work.lclpnet.translations.model.MutableLanguage;
import work.lclpnet.translations.model.StaticLanguageCollection;
import work.lclpnet.translations.util.TranslationParser;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

public class PartialJsonTranslationParser implements TranslationParser {

    private final Predicate<String> translationKeyPredicate;
    private final Map<String, MutableLanguage> languages = new HashMap<>();

    public PartialJsonTranslationParser(Predicate<String> translationKeyPredicate) {
        this.translationKeyPredicate = translationKeyPredicate;
    }

    @Override
    public void parse(InputStream input, String language) {
        var tok = new JSONTokener(input);
        var obj = new JSONObject(tok);

        var lang = languages.computeIfAbsent(language, key -> new MutableLanguage());

        for (String key : obj.keySet()) {
            if (!translationKeyPredicate.test(key)) continue;

            String translation = obj.optString(key);

            if (translation == null) continue;

            lang.add(key, translation);
        }
    }

    @Override
    public LanguageCollection build() {
        return new StaticLanguageCollection(languages);
    }
}
