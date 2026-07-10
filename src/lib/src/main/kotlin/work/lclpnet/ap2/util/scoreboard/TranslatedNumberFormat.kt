package work.lclpnet.ap2.util.scoreboard

import net.minecraft.network.chat.numbers.NumberFormat

/**
 * A [NumberFormat] that may differ per language, resolved for a viewer's language.
 */
fun interface TranslatedNumberFormat {

    fun translateTo(language: String): NumberFormat?

    companion object {
        fun constant(format: NumberFormat?): TranslatedNumberFormat {
            return TranslatedNumberFormat { language -> format }
        }
    }
}
