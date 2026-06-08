package work.lclpnet.ap2.ext

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.BossEvent
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.scores.DisplaySlot
import org.slf4j.Logger
import work.lclpnet.ap2.api.event.IntScoreEventSource
import work.lclpnet.ap2.impl.game.BaseGameInstance
import work.lclpnet.ap2.impl.game.FFAGameInstance
import work.lclpnet.ap2.impl.game.GameCommons
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.SoundHelper
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import work.lclpnet.game.util.BossBarTimer
import work.lclpnet.kibu.hook.entity.EntityHealthCallback
import work.lclpnet.kibu.translate.text.TranslatedText
import kotlin.time.Duration

fun BaseGameInstance.players() =
    gameHandle.participants!!

fun BaseGameInstance.allPlayers() =
    PlayerLookup.all(gameHandle.server)

fun BaseGameInstance.translate(key: String, vararg args: Any) =
    gameHandle.translations.translateText(key, *args)!!

val BaseGameInstance.logger: Logger
    get() = gameHandle.logger

val BaseGameInstance.server: MinecraftServer
    get() = gameHandle.server

fun FFAGameInstance.setupSidebarScoreboard(data: IntScoreEventSource<ServerPlayer>) {
    val objective = gameHandle.scoreboardManager.translateObjective("points", "ap2.score")

    useScoreboardStatsSync(data, objective)
    objective.setSlot(DisplaySlot.SIDEBAR)

    allPlayers().forEach(objective::add)
}

fun BaseGameInstance.readShape(key: String): BlockShape =
    MapUtil.readShape(map, key)

inline fun <reified T : LivingEntity> BaseGameInstance.onDeathOf(
    noinline action: (T, DamageSource) -> Unit
) {
    EntityHealthCallback.HOOK.registerWith(gameHandle.hooks) { entity, health ->
        if (entity !is T) {
            return@registerWith false
        }

        GameCommons.handleCustomDeath(entity, health, action)
    }
}

fun BaseGameInstance.playSound(
    sound: SoundEvent,
    source: SoundSource,
    volume: Float,
    pitch: Float
) =
    SoundHelper.playSound(level, sound, source, volume, pitch)

fun BaseGameInstance.createTimer(
    label: TranslatedText,
    duration: Duration,
    color: BossEvent.BossBarColor = BossEvent.BossBarColor.RED,
): BossBarTimer {
    val translations = gameHandle.translations

    val timer = BossBarTimer.builder(translations, label)
        .withAlertSound(false)
        .withColor(color)
        .withDurationTicks(duration.inWholeTicks.toInt())
        .build()

    timer.addPlayers(PlayerLookup.all(gameHandle.server))
    timer.start(gameHandle.bossBarProvider, gameHandle.scheduler)

    return timer
}
