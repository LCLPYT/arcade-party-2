package work.lclpnet.ap2.task_rush.task

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.data.Ordering
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.ap2.task_rush.util.TRSpawns
import work.lclpnet.ap2.task_rush.util.spawnRandomMobs
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks
import kotlin.time.Duration.Companion.seconds

object MobKillTask : Task {


    override val id = "mob_kills"

    val spawns = TRSpawns()

    override fun begin(env: TaskEnv) {
        val data = IntScoreDataContainer(
            PlayerRef::create,
            ordering = Ordering.DESCENDING,
            "score.mobs_killed"
        )

        for (player in env.players) {
            data.identityIfAbsent(player)

            env.give(player, ItemStack(Items.STONE_SWORD))
        }

        spawns.spawnRandomMobs(env, 35, minDistance = 10.0, maxDistance = 100.0)

        ServerLivingEntityHooks.AFTER_DEATH.registerWith(env.hooks) { _, source ->
            val killer = source.entity as? ServerPlayer ?: return@registerWith

            if (env.players.isParticipating(killer)) {
                data.addScore(killer, 1)
                env.feedback(killer, "task.feedback.mob_kills", data.getScore(killer), sound = true)
            }
        }

        env.timer("task.$id.task", 30.seconds) {
            env.complete(data)
        }
    }
}