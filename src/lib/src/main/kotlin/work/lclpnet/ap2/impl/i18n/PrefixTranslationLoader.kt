package work.lclpnet.ap2.impl.i18n

import work.lclpnet.ap2.impl.i18n.PrefixTranslationLoader.Companion.BASE_KEY
import work.lclpnet.translations.loader.TranslationLoader
import work.lclpnet.translations.model.Language
import work.lclpnet.translations.model.LanguageCollection
import work.lclpnet.translations.model.MutableLanguage
import work.lclpnet.translations.model.StaticLanguageCollection
import java.util.concurrent.CompletableFuture

/**
 * A [work.lclpnet.translations.loader.TranslationLoader] that wraps another loader and prepends a fixed prefix to every
 * translation key it provides.
 *
 *
 * This allows translation files to omit a common prefix. Keys are joined with the prefix using
 * a dot, except for the [BASE_KEY] key which maps to the bare prefix.
 */
class PrefixTranslationLoader(
    private val delegate: TranslationLoader,
    private val prefix: String,
) : TranslationLoader {

    override fun load(): CompletableFuture<out LanguageCollection> =
        delegate.load().thenApply {
            applyPrefix(it)
        }

    private fun applyPrefix(collection: LanguageCollection): LanguageCollection {
        val languages = mutableMapOf<String, Language>()

        for (languageName in collection.keys()) {
            val language = collection.get(languageName) ?: continue

            val prefixed = MutableLanguage()

            for (key in language.keys()) {
                prefixed.add(applyPrefix(key), language.get(key))
            }

            languages[languageName] = prefixed
        }

        return StaticLanguageCollection(languages)
    }

    private fun applyPrefix(key: String): String {
        if (BASE_KEY == key) {
            return prefix
        }

        return "$prefix.$key"
    }

    companion object {
        /**
         * The relative key that maps to the bare prefix (without a trailing dot).
         */
        const val BASE_KEY: String = "name"
    }
}