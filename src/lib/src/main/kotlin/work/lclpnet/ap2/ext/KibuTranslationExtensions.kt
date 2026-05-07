package work.lclpnet.ap2.ext

import work.lclpnet.kibu.translate.text.TranslatedText

fun TranslatedText.withColor(color: Int) =
    styled { style -> style.withColor(color) }!!