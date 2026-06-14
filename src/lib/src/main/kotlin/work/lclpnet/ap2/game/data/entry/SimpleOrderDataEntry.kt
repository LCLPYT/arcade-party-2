package work.lclpnet.ap2.game.data.entry

import work.lclpnet.ap2.api.game.data.DataEntry
import work.lclpnet.ap2.api.game.data.SubjectRef
import work.lclpnet.kibu.translate.Translations
import work.lclpnet.kibu.translate.text.TranslatedText

@JvmRecord
data class SimpleOrderDataEntry<Ref : SubjectRef>(
    override val subject: Ref,
    val order: Int,
    val data: TranslatedText?
) : DataEntry<Ref> {

    constructor(subject: Ref, order: Int) : this(subject, order, null)

    override fun toText(translationService: Translations): TranslatedText? {
        return data
    }

    override fun scoreEquals(other: DataEntry<Ref>): Boolean {
        if (other is SimpleOrderDataEntry<Ref>) {
            return order == other.order
        }

        return false
    }
}
