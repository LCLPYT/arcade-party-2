package work.lclpnet.ap2.game.data.entry

import work.lclpnet.ap2.game.data.DataEntry
import work.lclpnet.ap2.game.data.SubjectRef
import work.lclpnet.kibu.translate.Translations
import work.lclpnet.kibu.translate.text.LocalizedFormat
import work.lclpnet.kibu.translate.text.TranslatedText
import kotlin.math.abs

@JvmRecord
data class DoubleScoreDataEntry<Ref : SubjectRef>(
    override val subject: Ref,
    val score: Double,
    val format: String,
    val keyOverride: String?
) : DataEntry<Ref> {

    override fun toText(translationService: Translations): TranslatedText {
        val key: String = keyOverride ?: "ap2.score.points"
        return translationService.translateText(key, LocalizedFormat.format(format, score))
    }

    override fun scoreEquals(other: DataEntry<Ref>): Boolean {
        if ((other is DoubleScoreDataEntry<Ref>)) {
            return abs(score - other.score) < 1e-10
        }

        return false
    }
}
