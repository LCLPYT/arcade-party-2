package work.lclpnet.ap2.ext

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.scores.DisplaySlot
import org.slf4j.Logger
import work.lclpnet.ap2.game.MiniGameInstance
import work.lclpnet.ap2.game.base.FFAGameInstance
import work.lclpnet.ap2.game.base.MapGameInstance
import work.lclpnet.ap2.game.data.ScoreListenerView
import work.lclpnet.ap2.game.util.useScoreboardStatsSync
import work.lclpnet.ap2.impl.game.GameCommons
import work.lclpnet.ap2.impl.map.MapUtil
import work.lclpnet.ap2.impl.util.SoundHelper
import work.lclpnet.ap2.impl.util.world.block_shape.BlockShape
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.hook.entity.EntityHealthCallback
import work.lclpnet.kibu.scheduler.api.TaskScheduler
import work.lclpnet.kibu.translate.Translations

fun MiniGameInstance.players() =
    gameHandle.participants

fun MiniGameInstance.allPlayers() =
    PlayerLookup.all(gameHandle.server)

fun MiniGameInstance.translate(key: String, vararg args: Any) =
    gameHandle.translations.translateText(key, *args)!!

val MiniGameInstance.logger: Logger
    get() = gameHandle.logger

val MiniGameInstance.server: MinecraftServer
    get() = gameHandle.server

val MiniGameInstance.scheduler: TaskScheduler
    get() = gameHandle.scheduler

val MiniGameInstance.translations: Translations
    get() = gameHandle.translations

val MiniGameInstance.hooks: HookRegistrar
    get() = gameHandle.hooks

fun MiniGameInstance.isParticipating(player: ServerPlayer): Boolean =
    gameHandle.participants.isParticipating(player)

inline fun <reified T : LivingEntity> MiniGameInstance.onDeathOf(
    noinline action: (T, DamageSource) -> Unit
) {
    EntityHealthCallback.HOOK.registerWith(hooks) { entity, health ->
        if (entity !is T) {
            return@registerWith false
        }

        GameCommons.handleCustomDeath(entity, health, action)
    }
}

fun MiniGameInstance.playSound(
    sound: SoundEvent,
    source: SoundSource,
    volume: Float,
    pitch: Float
) =
    SoundHelper.playSound(level, sound, source, volume, pitch)

fun FFAGameInstance.setupSidebarScoreboard(data: ScoreListenerView<ServerPlayer, Int>) {
    val objective = gameHandle.scoreboardManager.translateObjective("points", "ap2.score")

    useScoreboardStatsSync(data, objective)
    objective.setSlot(DisplaySlot.SIDEBAR)

    allPlayers().forEach(objective::add)
}

fun MapGameInstance.readShape(key: String): BlockShape =
    MapUtil.readShape(map, key)