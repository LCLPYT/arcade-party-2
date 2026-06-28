package work.lclpnet.ap2.game.guess_it.data

import net.minecraft.network.chat.Component
import work.lclpnet.kibu.translate.text.TranslatedText

interface ChallengeMessenger {

    fun task(task: TranslatedText)

    fun options(vararg options: Component)
}
