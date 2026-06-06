package work.lclpnet.ap2.api.stats

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.impl.util.TimeHelper
import work.lclpnet.kibu.translate.Translations
import java.util.*

fun interface StatUnit {
    fun format(value: Any, player: ServerPlayer, translations: Translations): Component
}

object StatUnits {
    val Plain = StatUnit { value, player, translations ->
        Component.literal(formatValuePlain(value, translations.getLocale(player)))
    }

    val Percent = StatUnit { value, player, translations ->
        val percent = (value as Number).toDouble() * 100.0
        Component.literal(String.format(translations.getLocale(player), "%.1f", percent) + "%")
    }

    val Seconds = StatUnit { value, player, translations ->
        val text = if (value is Int) TimeHelper.formatTime(translations, value)
                   else TimeHelper.formatTime(translations, (value as Number).toDouble())
        text.translateFor(player)
    }

    private fun formatValuePlain(value: Any, locale: Locale): String = when (value) {
        is Float, is Double -> String.format(locale, "%.2f", value)
        else -> value.toString()
    }
}
