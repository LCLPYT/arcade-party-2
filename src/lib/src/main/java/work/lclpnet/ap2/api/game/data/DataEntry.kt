package work.lclpnet.ap2.api.game.data

import work.lclpnet.kibu.translate.Translations
import work.lclpnet.kibu.translate.text.TranslatedText

interface DataEntry<Ref : SubjectRef> {

    val subject: Ref

    fun toText(translationService: Translations): TranslatedText?

    fun scoreEquals(other: DataEntry<Ref>): Boolean
}
