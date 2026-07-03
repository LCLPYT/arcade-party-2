package work.lclpnet.ap2.game.data.entry

import work.lclpnet.ap2.game.data.DataEntry
import work.lclpnet.ap2.game.data.SubjectRef
import work.lclpnet.kibu.translate.Translations
import work.lclpnet.kibu.translate.text.TranslatedText

@JvmRecord
data class ScoreTimeDataEntry<Ref : SubjectRef>(
    override val subject: Ref,
    override val score: Int,
    val keyOverride: String?,
    val ranking: Int
) : DataEntry<Ref>, ScoreView {

    override fun toText(translationService: Translations): TranslatedText {
        val key = keyOverride ?: "ap2.score.points_timed"

        return translationService.translateText(key, score, ranking)
    }

    override fun scoreEquals(other: DataEntry<Ref>): Boolean {
        if ((other is ScoreTimeDataEntry<Ref>)) {
            return score == other.score && ranking == other.ranking
        }

        return false
    }
}
