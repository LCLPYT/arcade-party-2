package work.lclpnet.ap2.impl.i18n

import work.lclpnet.kibu.translate.Translations
import work.lclpnet.kibu.translate.util.ScopedTranslator
import work.lclpnet.translations.Translator

/**
 * A [work.lclpnet.translations.Translator] that scopes lookups to a single mini-game by prepending its translation key
 * prefix, mirroring [PrefixTranslationLoader].
 *
 *
 * Relative keys are resolved against the prefix (`prefix + "." + key`), with the special
 * [PrefixTranslationLoader.BASE_KEY] mapping to the bare prefix. If no prefixed translation
 * exists, the lookup falls back to the raw key. This allows mini-game code to use relative keys while
 * still resolving shared keys (e.g. `ap2.*` or vanilla `death.*`) that are not scoped to a
 * specific game.
 */
class GameScopedTranslator(parent: Translator, titleKey: String) : ScopedTranslator(parent, titleKey) {
    override fun prefixed(key: String): String {
        if (PrefixTranslationLoader.BASE_KEY == key) {
            return prefix
        }

        return "$prefix.$key"
    }

    override fun translate(locale: String, key: String): String {
        val scoped = prefixed(key)

        if (parent.hasTranslation(locale, scoped)) {
            return parent.translate(locale, scoped)
        }

        return parent.translate(locale, key)
    }

    override fun hasTranslation(locale: String, key: String): Boolean =
        parent.hasTranslation(locale, prefixed(key)) || parent.hasTranslation(locale, key)

    companion object {
        /**
         * Create a [work.lclpnet.kibu.translate.Translations] view of the given parent that scopes lookups to a mini-game.
         *
         * @param parent   The parent translations to delegate to.
         * @param titleKey The mini-game title key used as the prefix (e.g. `game.ap2.foo`).
         * @return A scoped [work.lclpnet.kibu.translate.Translations] instance with fallback to unscoped keys.
         */
        fun scope(parent: Translations, titleKey: String): Translations =
            Translations(GameScopedTranslator(parent.translator, titleKey))
    }
}