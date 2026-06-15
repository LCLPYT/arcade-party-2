package work.lclpnet.ap2.game.util

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.ChatFormatting
import net.minecraft.world.BossEvent
import work.lclpnet.ap2.ApConstants
import work.lclpnet.ap2.ext.server
import work.lclpnet.ap2.ext.translate
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.impl.util.bossbar.DynamicTranslatedPlayerBossBar
import work.lclpnet.game.util.BossBarTimer
import work.lclpnet.kibu.translate.bossbar.TranslatedBossBar
import kotlin.time.Duration

fun MiniGameInstance.useTaskTimer(duration: Duration): BossBarTimer {
    val subject = translate(gameHandle.gameInfo.taskKey)

    return createTimer(subject, duration)
}

fun MiniGameInstance.useTaskDisplay(): TranslatedBossBar {
    val gameInfo = gameHandle.gameInfo
    val id = gameInfo.identifier("task")

    val bossBar = gameHandle.translations.translateBossBar(id, gameInfo.taskKey, *gameInfo.taskArguments)
        .with(gameHandle.bossBarProvider)
        .formatted(ChatFormatting.GREEN)

    bossBar.setColor(BossEvent.BossBarColor.GREEN)

    bossBar.addPlayers(PlayerLookup.all(server))

    gameHandle.bossBarHandler.showOnJoin(bossBar)

    return bossBar
}

@JvmOverloads
fun MiniGameInstance.usePlayerDynamicTaskDisplay(
    vararg args: Any?,
    key: String = gameHandle.gameInfo.taskKey,
): DynamicTranslatedPlayerBossBar {
    val id = ApConstants.identifier("task")

    val translations = gameHandle.translations
    val provider = gameHandle.bossBarProvider

    val bossBar = DynamicTranslatedPlayerBossBar(id, key, args, translations, provider)
        .formatted(ChatFormatting.GREEN)

    bossBar.setColor(BossEvent.BossBarColor.GREEN)
    bossBar.setPercent(1f)

    for (player in gameHandle.participants) {
        bossBar.add(player)
    }

    bossBar.init(gameHandle.hooks)

    return bossBar
}
