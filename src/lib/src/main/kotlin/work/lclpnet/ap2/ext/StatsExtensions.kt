package work.lclpnet.ap2.ext

import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.api.stats.CommonStats
import work.lclpnet.ap2.api.stats.FFAStatsManager
import work.lclpnet.ap2.impl.game.BaseGameInstance
import work.lclpnet.kibu.hook.player.PlayerMoveCallback
import work.lclpnet.kibu.hook.util.PositionRotation
import kotlin.math.sqrt

/**
 * Registers a movement listener that updates the distance traveled for each player.
 * This method will track all movement events, also those that may be canceled by other hooks.
 * If needed, use [updateDistanceMoved] directly.
 *
 * @param stats The stats manager that holds [CommonStats.DistanceMoved].
 */
fun BaseGameInstance.trackDistanceMoved(stats: FFAStatsManager) {
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
fun BaseGameInstance.updateDistanceMoved(
    stats: FFAStatsManager,
    player: ServerPlayer,
    from: PositionRotation,
    to: PositionRotation
) {
    if (!gameHandle.participants.isParticipating(player)) return

    val dx = to.x() - from.x()
    val dz = to.z() - from.z()

    stats.modify(player, CommonStats.DistanceMoved) { it + sqrt(dx * dx + dz * dz).toFloat() }
}
