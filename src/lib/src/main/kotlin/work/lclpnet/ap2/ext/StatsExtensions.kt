package work.lclpnet.ap2.ext

import net.minecraft.ChatFormatting
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import work.lclpnet.ap2.api.stats.BaseStatsManager
import work.lclpnet.ap2.api.stats.CommonStats
import work.lclpnet.ap2.api.stats.CommonStats.Kills
import work.lclpnet.ap2.api.stats.FFAStatsManager
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.ap2.game.base.MapGameInstance
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.kibu.hook.player.PlayerMoveCallback
import work.lclpnet.kibu.hook.util.PositionRotation
import work.lclpnet.kibu.translate.Translations
import work.lclpnet.kibu.translate.text.FormatWrapper
import kotlin.math.sqrt

/**
 * Registers a movement listener that updates the distance traveled for each player.
 * This method will track all movement events, also those that may be canceled by other hooks.
 * If needed, use [updateDistanceMoved] directly.
 *
 * @param stats The stats manager that holds [CommonStats.DistanceMoved].
 */
fun MapGameInstance.trackDistanceMoved(stats: FFAStatsManager) {
    PlayerMoveCallback.HOOK.registerWith(gameHandle.hooks) { player, from, to ->
        updateDistanceMoved(stats, player, from, to)
        false
    }
}
/**
 * Accumulates the horizontal distance a participant traveled into [CommonStats.DistanceMoved].
 *
 * Intended to be called from a [work.lclpnet.kibu.hook.player.PlayerMoveCallback] handler. Movement of
 * players that are not participating is ignored.
 *
 * @param stats The stats manager that holds [CommonStats.DistanceMoved].
 * @param player The player that moved.
 * @param from The position the player moved from.
 * @param to The position the player moved to.
 */
fun MapGameInstance.updateDistanceMoved(
    stats: FFAStatsManager,
    player: ServerPlayer,
    from: PositionRotation,
    to: PositionRotation
) {
    if (!gameHandle.participants.isParticipating(player)) return

    val dx = to.x() - from.x()
    val dz = to.z() - from.z()

    stats.modify(player, CommonStats.DistanceMoved) {
        it + sqrt(dx * dx + dz * dz)
    }
}

fun MapGameInstance.gainKill(player: ServerPlayer, stats: BaseStatsManager<ServerPlayer, PlayerRef>) {
    gainKill(player, stats, gameHandle.translations)
}

fun gainKill(player: ServerPlayer, stats: BaseStatsManager<ServerPlayer, PlayerRef>, translations: Translations) {
    stats.increment(player, Kills)

    player.playNotifySound(SoundEvents.ARROW_HIT_PLAYER, SoundSource.BLOCKS, 0.7f, 1.55f)

    translations.translateText("ap2.gain_kill", FormatWrapper.styled(1, ChatFormatting.YELLOW))
        .withStyle(ChatFormatting.GREEN)
        .sendTo(player, true)
}
