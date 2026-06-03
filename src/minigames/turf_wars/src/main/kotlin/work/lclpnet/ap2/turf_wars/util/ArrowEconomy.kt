package work.lclpnet.ap2.turf_wars.util

import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.api.game.MiniGameHandle
import work.lclpnet.ap2.api.game.team.TeamManager
import work.lclpnet.ap2.ext.inWholeTicks
import work.lclpnet.ap2.ext.mc.playNotifySound
import work.lclpnet.ap2.turf_wars.ARROW_GAIN_DEFICIT_DELAY
import work.lclpnet.ap2.turf_wars.ARROW_GAIN_DELAY
import work.lclpnet.ap2.turf_wars.MAX_ARROWS
import work.lclpnet.ap2.turf_wars.MAX_ARROWS_DEFICIT
import work.lclpnet.kibu.scheduler.api.TaskHandle
import java.util.*
import kotlin.time.Duration

/**
 * Manages the arrow ammunition each player carries. Players slowly refill arrows up to a cap,
 * and outnumbered players refill faster and carry one more arrow to compensate.
 */
class ArrowEconomy(
    private val gameHandle: MiniGameHandle,
    private val teamManager: TeamManager
) {

    private val refillTasks = mutableMapOf<UUID, TaskHandle>()

    fun giveArrow(player: ServerPlayer) {
        val count = player.inventory.countItem(Items.ARROW)

        if (count >= maxArrows(player)) return

        val stack = ItemStack(Items.ARROW)

        if (count == 0) {
            player.inventory.setItem(8, stack)
        } else {
            player.inventory.add(stack)
        }

        player.playNotifySound(SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3f, 2f)
    }

    fun scheduleRefill(player: ServerPlayer) {
        if (player.inventory.countItem(Items.ARROW) >= maxArrows(player)) return

        if (refillTasks[player.uuid] != null) return

        refillTasks[player.uuid] = gameHandle.scheduler.timeout(gainDelay(player).inWholeTicks, Runnable {
            giveArrow(player)
            refillTasks.remove(player.uuid)
            scheduleRefill(player)
        })
    }

    fun cancelRefill(player: ServerPlayer) {
        refillTasks[player.uuid]?.cancel()
        refillTasks.remove(player.uuid)
    }

    private fun isOutnumbered(player: ServerPlayer): Boolean {
        val team = teamManager.getTeam(player).orElse(null) ?: return false
        val ownSize = team.playerCount
        val maxSize = teamManager.teams.maxOfOrNull { it.playerCount } ?: return false

        return ownSize < maxSize
    }

    private fun maxArrows(player: ServerPlayer): Int =
        if (isOutnumbered(player)) MAX_ARROWS_DEFICIT else MAX_ARROWS

    private fun gainDelay(player: ServerPlayer): Duration =
        if (isOutnumbered(player)) ARROW_GAIN_DEFICIT_DELAY else ARROW_GAIN_DELAY
}
