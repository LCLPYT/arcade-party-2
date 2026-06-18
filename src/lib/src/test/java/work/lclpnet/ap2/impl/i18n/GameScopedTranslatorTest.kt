package work.lclpnet.ap2.impl.i18n;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import work.lclpnet.translations.Translator;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GameScopedTranslatorTest {

    private final Translator parent = new MapTranslator(Map.of(
            "game.ap2.foo", "Foo",
            "game.ap2.foo.prepare", "Next round",
            "ap2.go", "Go!"
    ));

    private final GameScopedTranslator translator = new GameScopedTranslator(parent, "game.ap2.foo");

    @Test
    void translate_relativeKey_resolvesPrefixed() {
        assertEquals("Next round", translator.translate("en_us", "prepare"));
    }

    @Test
    void translate_baseKey_resolvesBareTitle() {
        assertEquals("Foo", translator.translate("en_us", "name"));
    }

    @Test
    void translate_nonGameKey_fallsBackToUnscoped() {
        assertEquals("Go!", translator.translate("en_us", "ap2.go"));
    }

    @Test
    void translate_fullKey_resolvesViaFallback() {
        assertEquals("Next round", translator.translate("en_us", "game.ap2.foo.prepare"));
    }

    @Test
    void hasTranslation_reflectsPrefixedAndFallback() {
        assertTrue(translator.hasTranslation("en_us", "prepare"));
        assertTrue(translator.hasTranslation("en_us", "name"));
        assertTrue(translator.hasTranslation("en_us", "ap2.go"));
        assertFalse(translator.hasTranslation("en_us", "missing"));
    }

    private record MapTranslator(Map<String, String> translations) implements Translator {

        @Override
        public @NotNull String translate(String locale, String key) {
            return translations.getOrDefault(key, key);
        }

        @Override
        public boolean hasTranslation(String locale, String key) {
            return translations.containsKey(key);
        }

        @Override
        public @NotNull SimpleDateFormat getDateFormat(String locale) {
            return new SimpleDateFormat();
        }

        @Override
        public Iterable<String> getLanguages() {
            return List.of("en_us");
        }
    }
}
