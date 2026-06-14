package work.lclpnet.ap2.game.data.entry

import work.lclpnet.ap2.api.game.data.DataEntry
import work.lclpnet.ap2.api.game.data.SubjectRef
import work.lclpnet.kibu.translate.Translations
import work.lclpnet.kibu.translate.text.TranslatedText

@JvmRecord
data class SupremeDataEntry<Ref : SubjectRef>(
    override val subject: Ref
) : DataEntry<Ref> {

    override fun toText(translationService: Translations): TranslatedText? {
        return null
    }

    override fun scoreEquals(other: DataEntry<Ref>): Boolean {
        return other.javaClass == this.javaClass
    }
}
