package work.lclpnet.ap2.game.data.entry

import work.lclpnet.ap2.api.game.data.DataEntry
import work.lclpnet.ap2.api.game.data.SubjectRef
import work.lclpnet.kibu.translate.Translations
import work.lclpnet.kibu.translate.text.TranslatedText

@JvmRecord
data class SimpleDataEntry<Ref : SubjectRef>(
    override val subject: Ref,
    val data: TranslatedText?
) : DataEntry<Ref> {

    constructor(subject: Ref) : this(subject, null)

    override fun toText(translationService: Translations): TranslatedText? {
        return data
    }

    override fun scoreEquals(other: DataEntry<Ref>): Boolean {
        return other === this
    }
}
