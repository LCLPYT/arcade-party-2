package work.lclpnet.ap2.game.guess_it.data

import net.minecraft.ChatFormatting
import work.lclpnet.kibu.translate.Translations
import work.lclpnet.kibu.translate.text.FormatWrapper
import work.lclpnet.kibu.translate.text.TranslatedText

class OptionValue(translations: Translations, options: Int) {

    private val translations: Translations
    private val options: Int

    init {
        require(options >= 2) { "There must be at least two options" }

        this.translations = translations
        this.options = options
    }

    fun validate(input: String): Pair<String?, TranslatedText?> {
        val option = parseOption(input)

        if (option == null) {
            val from = 'A'
            val to = ('A'.code + options - 1).toChar()

            val err = translations.translateText(
                "input.option",
                FormatWrapper.styled(input, ChatFormatting.YELLOW),
                FormatWrapper.styled(from, ChatFormatting.YELLOW),
                FormatWrapper.styled(to, ChatFormatting.YELLOW)
            ).withStyle(ChatFormatting.RED)

            return null to err
        }

        return option to null
    }

    private fun parseOption(input: String): String? {
        var input = input.trim()

        if (input.endsWith(")")) {
            input = input.substring(0, input.length - 1)
        }

        if (input.length == 1) {
            val c: Char = Character.toUpperCase(input[0])
            val index = c.code - 'A'.code

            if (index in 0..<options) {
                return c.toString()
            }
        }

        var num = input.toIntOrNull() ?: return null

        num -= 1

        if (num in 0..<options) {
            val letter = ('A'.code + num).toChar()
            return letter.toString()
        }

        return null
    }
}
