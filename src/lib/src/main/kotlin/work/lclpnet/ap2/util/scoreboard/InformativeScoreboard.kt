package work.lclpnet.ap2.util.scoreboard

import net.minecraft.network.chat.Component
import work.lclpnet.kibu.translate.text.TranslatedText

interface InformativeScoreboard {

    fun createText(text: Component, position: Int): ScoreHandle

    fun createText(text: TranslatedText, position: Int): ScoreHandle

    fun createText(text: Component): ScoreHandle =
        createText(text, ScoreboardLayout.TOP)

    fun createText(text: TranslatedText): ScoreHandle =
        createText(text, ScoreboardLayout.TOP)

    fun createNewline(position: Int) {
        createText(Component.empty(), position)
    }
}
