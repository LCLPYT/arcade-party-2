package work.lclpnet.ap2.impl.i18n;

import org.junit.jupiter.api.Test;
import work.lclpnet.translations.loader.TranslationLoader;
import work.lclpnet.translations.model.Language;
import work.lclpnet.translations.model.LanguageCollection;
import work.lclpnet.translations.model.MutableLanguage;
import work.lclpnet.translations.model.StaticLanguageCollection;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class PrefixTranslationLoaderTest {

    @Test
    void load_prefixesKeys() throws ExecutionException, InterruptedException {
        MutableLanguage en = new MutableLanguage();
        en.add("name", "Foo");
        en.add("task", "Do the thing");
        en.add("stat.clicks", "Clicks");

        TranslationLoader delegate = () -> CompletableFuture.completedFuture(
                new StaticLanguageCollection(Map.of("en_us", en)));

        var loader = new PrefixTranslationLoader(delegate, "game.ap2.foo");

        LanguageCollection collection = loader.load().get();
        Language language = collection.get("en_us");

        assertNotNull(language);
        assertEquals("Foo", language.get("game.ap2.foo"));
        assertEquals("Do the thing", language.get("game.ap2.foo.task"));
        assertEquals("Clicks", language.get("game.ap2.foo.stat.clicks"));
        assertNull(language.get("name"));
        assertNull(language.get("task"));
    }
}
