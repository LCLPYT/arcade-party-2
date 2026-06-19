package work.lclpnet.ap2.game.team

import net.minecraft.world.scores.TeamColor
import work.lclpnet.kibu.translate.Translations
import work.lclpnet.kibu.translate.text.TranslatedText

interface TeamKey {

    val id: String

    val color: Int

    val teamColor: TeamColor

    val translationKey: String
        get() = "ap2.team.$id"

    fun getDisplayName(translations: Translations): TranslatedText =
        translations.translateText(this.translationKey)
            .withColor(color)
}
