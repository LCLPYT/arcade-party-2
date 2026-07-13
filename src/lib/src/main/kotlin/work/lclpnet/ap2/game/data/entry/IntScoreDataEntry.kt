package work.lclpnet.ap2.game.data.entry

import work.lclpnet.ap2.game.data.DataEntry
import work.lclpnet.ap2.game.data.SubjectRef
import work.lclpnet.kibu.translate.Translations
import work.lclpnet.kibu.translate.text.TranslatedText

@JvmRecord
data class IntScoreDataEntry<Ref : SubjectRef>(
    override val subject: Ref,
    override val score: Int,
    val keyOverride: String?
) : DataEntry<Ref>, ScoreView {

    override fun toText(translationService: Translations): TranslatedText {
        val key: String = keyOverride ?: "ap2.score.points"
        return translationService.translateText(key, score)
    }

    override fun scoreEquals(other: DataEntry<Ref>): Boolean {
        if ((other is IntScoreDataEntry<Ref>)) {
            return score == other.score
        }

        return false
    }
}
