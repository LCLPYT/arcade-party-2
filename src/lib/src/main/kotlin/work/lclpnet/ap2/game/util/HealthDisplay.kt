package work.lclpnet.ap2.game.util

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.numbers.BlankFormat
import net.minecraft.network.chat.numbers.FixedFormat
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import work.lclpnet.ap2.core.hook.PlayerEliminatedCallback
import work.lclpnet.ap2.game.MiniGameHandle
import work.lclpnet.kibu.hook.HookRegistrar
import work.lclpnet.kibu.hook.entity.EntityHealthCallback
import kotlin.math.ceil

class HealthDisplay(private val gameHandle: MiniGameHandle) {

    fun setup(hooks: HookRegistrar) {
        val manager = gameHandle.scoreboardManager

        val objective = manager.createObjective(
            "health_name",
            ObjectiveCriteria.DUMMY,
            Component.empty(),
            ObjectiveCriteria.RenderType.HEARTS
        )

        objective.setDisplayAutoUpdate(false)

        manager.setDisplay(DisplaySlot.BELOW_NAME, objective)
        manager.setDisplay(DisplaySlot.LIST, objective)

        for (player in gameHandle.participants) {
            val health = player.health
            update(player, health, objective)
        }

        EntityHealthCallback.HOOK.registerWith(hooks) { entity, health ->
            val oldHealth = entity.health

            if (entity is ServerPlayer && gameHandle.participants.isParticipating(entity) && health < oldHealth) {
                // update the scoreboard
                update(entity, health, objective)
            }

            false
        }

        PlayerEliminatedCallback.HOOK.registerWith(hooks) { player ->
            manager.setScore(player, objective, 0)
            manager.setNumberFormat(player, objective, BlankFormat.INSTANCE)
        }
    }

    private fun update(player: ServerPlayer, health: Float, objective: Objective) {
        val manager = gameHandle.scoreboardManager

        manager.setScore(player, objective, ceil(health.toDouble()).toInt())
        manager.setNumberFormat(player, objective, FixedFormat(healthText(health)))
    }

    private fun healthText(health: Float): Component {
        var hearts = Math.clamp(ceil(health.toDouble()).toInt().toLong(), 0, 20)
        val half = hearts % 2 == 1
        hearts = hearts shr 1

        val text = Component.literal(" " + "♥".repeat(hearts)).withColor(0xff1313)

        if (half) {
            text.append(Component.literal("♡").withColor(0xff1313))
            hearts += 1
        }

        if (hearts < 10) {
            text.append(Component.literal("♡".repeat(10 - hearts)).withColor(0x282828))
        }

        return text
    }
}