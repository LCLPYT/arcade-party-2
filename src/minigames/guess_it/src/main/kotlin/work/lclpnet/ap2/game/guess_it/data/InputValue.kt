package work.lclpnet.ap2.game.guess_it.data

import net.minecraft.ChatFormatting
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.kibu.translate.Translations
import work.lclpnet.kibu.translate.text.FormatWrapper
import work.lclpnet.kibu.translate.text.TranslatedText
import java.util.function.Function

class InputValue {

    private val rules = ArrayList<InputRule>()

    var once = false
        private set

    fun validate(validator: InputParser, errorMessage: Function<String, TranslatedText>): InputValue {
        rules.add(InputRule(validator, errorMessage))
        return this
    }

    fun validateInt(translations: Translations): InputValue = validate(
        { s, _ -> intValue(s) },
        { input ->
            translations.translateText(
                "input.int",
                FormatWrapper.styled(input, ChatFormatting.YELLOW)
            ).withStyle(ChatFormatting.RED)
        }
    )

    fun validateFloat(translations: Translations, precision: Int): InputValue = validate(
        { input, player ->
            floatValue(
                input,
                player,
                translations,
                precision
            )
        },
        { input ->
            translations.translateText(
                "input.float",
                FormatWrapper.styled(input, ChatFormatting.YELLOW)
            ).withStyle(ChatFormatting.RED)
        }
    )

    fun onlyOnce(): InputValue {
        once = true
        return this
    }

    fun validate(input: String, player: ServerPlayer): Pair<String?, TranslatedText?> {
        var input = input

        for (rule in rules) {
            val res = rule.parser.parse(input, player) ?: return input to rule.errorMessage.apply(input)

            input = res
        }

        return input to null
    }

    fun interface InputParser {
        fun parse(input: String, player: ServerPlayer): String?
    }

    private data class InputRule(
        val parser: InputParser,
        val errorMessage: Function<String, TranslatedText>
    )

    companion object {
        private fun floatValue(s: String, player: ServerPlayer, translations: Translations, precision: Int): String? {
            val s = s.replace(',', '.')
            val f = s.toFloatOrNull() ?: return null

            val fmt = "%." + Math.clamp(precision.toLong(), 0, 7) + "f"
            val locale = translations.getLocale(player)

            return String.format(locale, fmt, f)
        }

        fun intValue(s: String): String? =
            s.toIntOrNull()?.toString()
    }
}
