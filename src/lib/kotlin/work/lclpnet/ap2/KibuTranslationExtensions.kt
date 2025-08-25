package work.lclpnet.ap2

import work.lclpnet.kibu.translate.text.TranslatedText

fun TranslatedText.withColor(color: Int) =
    styled { style -> style.withColor(color) }!!