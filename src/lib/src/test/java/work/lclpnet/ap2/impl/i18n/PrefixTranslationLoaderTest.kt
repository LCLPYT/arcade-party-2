package work.lclpnet.ap2.impl.i18n

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import work.lclpnet.translations.loader.TranslationLoader
import work.lclpnet.translations.model.MutableLanguage
import work.lclpnet.translations.model.StaticLanguageCollection
import java.util.concurrent.CompletableFuture

class PrefixTranslationLoaderTest {

    @Test
    fun load_prefixesKeys() {
        val en = MutableLanguage()
        en.add("name", "Foo")
        en.add("task", "Do the thing")
        en.add("stat.clicks", "Clicks")

        val delegate = TranslationLoader {
            CompletableFuture.completedFuture(
                StaticLanguageCollection(mapOf("en_us" to en))
            )
        }

        val loader = PrefixTranslationLoader(delegate, "game.ap2.foo")

        val collection = loader.load().get()
        val language = collection.get("en_us")

        assertNotNull(language)
        assertEquals("Foo", language!!.get("game.ap2.foo"))
        assertEquals("Do the thing", language.get("game.ap2.foo.task"))
        assertEquals("Clicks", language.get("game.ap2.foo.stat.clicks"))
        assertNull(language.get("name"))
        assertNull(language.get("task"))
    }
}
