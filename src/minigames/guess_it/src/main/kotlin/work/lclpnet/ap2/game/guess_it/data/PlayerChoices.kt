package work.lclpnet.ap2.game.guess_it.data

import net.minecraft.server.level.ServerPlayer
import work.lclpnet.kibu.translate.Translations
import java.text.NumberFormat
import java.text.ParseException
import java.util.*

class PlayerChoices(private val translations: Translations) {
    private val choices = HashMap<UUID, String>()

    fun set(player: ServerPlayer, choice: String) {
        choices[player.getUUID()] = choice
    }

    fun get(player: ServerPlayer): String? =
        choices[player.getUUID()]

    fun getInt(player: ServerPlayer): Int? {
        val c = choices[player.getUUID()] ?: return null

        return c.toIntOrNull()
    }

    fun getFloat(player: ServerPlayer): Float? {
        val c = choices[player.getUUID()] ?: return null

        val locale = translations.getLocale(player)
        val format = NumberFormat.getInstance(locale)

        return try {
            format.parse(c).toFloat()
        } catch (_: ParseException) {
            null
        }
    }

    fun getOption(player: ServerPlayer): Int? {
        val input = choices[player.getUUID()]

        if (input == null || input.length != 1) {
            return null
        }

        val c: Char = input[0]
        val option = c.code - 'A'.code

        return option
    }

    fun clear() {
        choices.clear()
    }
}
