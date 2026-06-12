package work.lclpnet.ap2.game.util

import work.lclpnet.ap2.ext.translate
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.game.util.BossBarTimer

fun MiniGameInstance.useTaskTimer(seconds: Int): BossBarTimer {
    val subject = translate(gameHandle.gameInfo.taskKey)

    return createTimer(subject, seconds)
}
