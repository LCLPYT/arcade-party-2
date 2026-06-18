package work.lclpnet.ap2.impl.i18n

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import work.lclpnet.translations.Translator
import java.text.SimpleDateFormat

internal class GameScopedTranslatorTest {
    private val parent: Translator = MapTranslator(
        mapOf(
            "game.ap2.foo" to "Foo",
            "game.ap2.foo.prepare" to "Next round",
            "ap2.go" to "Go!"
        )
    )

    private val translator = GameScopedTranslator(parent, "game.ap2.foo")

    @Test
    fun translate_relativeKey_resolvesPrefixed() {
        assertEquals("Next round", translator.translate("en_us", "prepare"))
    }

    @Test
    fun translate_baseKey_resolvesBareTitle() {
        assertEquals("Foo", translator.translate("en_us", "name"))
    }

    @Test
    fun translate_nonGameKey_fallsBackToUnscoped() {
        assertEquals("Go!", translator.translate("en_us", "ap2.go"))
    }

    @Test
    fun translate_fullKey_resolvesViaFallback() {
        assertEquals("Next round", translator.translate("en_us", "game.ap2.foo.prepare"))
    }

    @Test
    fun hasTranslation_reflectsPrefixedAndFallback() {
        assertTrue(translator.hasTranslation("en_us", "prepare"))
        assertTrue(translator.hasTranslation("en_us", "name"))
        assertTrue(translator.hasTranslation("en_us", "ap2.go"))
        assertFalse(translator.hasTranslation("en_us", "missing"))
    }

    private data class MapTranslator(val translations: Map<String, String>) : Translator {

        override fun translate(locale: String, key: String): String = translations.getOrDefault(key, key)

        override fun hasTranslation(locale: String, key: String): Boolean = translations.containsKey(key)

        override fun getDateFormat(locale: String): SimpleDateFormat = SimpleDateFormat()

        override fun getLanguages(): Iterable<String> = listOf("en_us")
    }
}
