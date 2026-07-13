package work.lclpnet.ap2.task_rush.task

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.game.data.DoubleScoreDataContainer
import work.lclpnet.ap2.game.data.Ordering
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.ap2.task_rush.task.MobKillTask.spawns
import work.lclpnet.ap2.task_rush.util.spawnRandomMobs
import work.lclpnet.kibu.hook.entity.EntityDamageCallback
import java.util.*
import kotlin.time.Duration.Companion.seconds

/**
 * Deal the most damage to entities.
 */
object EntityDamageTask : Task {

    override val id = "entity_damage"

    override fun begin(env: TaskEnv) {
        val data = DoubleScoreDataContainer(PlayerRef::create, Ordering.DESCENDING, "score.damage_dealt")

        for (player in env.players) {
            data.setScore(player, 0.0)

            env.giveIfMissing(player, ItemStack(Items.STONE_SWORD))
        }

        spawns.spawnRandomMobs(env, 25, minDistance = 25.0, maxDistance = 90.0)

        EntityDamageCallback.HOOK.registerWith(env.hooks) { victim, source, amount ->
            val attacker = source.entity as? ServerPlayer

            if (attacker != null && env.players.isParticipating(attacker) && victim !is ServerPlayer && amount > 0f) {
                data.addScore(attacker, amount.coerceAtMost(victim.health).toDouble())
                env.feedback(attacker, "task.feedback.entity_damage", String.format(Locale.ROOT, "%.1f", data.getScore(attacker)))
            }

            false
        }

        env.timer("task.$id.task", 30.seconds) {
            env.complete(data)
        }
    }
}
