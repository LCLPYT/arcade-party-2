package work.lclpnet.ap2.task_rush.task

import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.data.Ordering
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.kibu.hook.entity.ServerLivingEntityHooks
import kotlin.time.Duration.Companion.seconds

object MobKillTask : Task {

    override val id = "mob_kills"

    override fun begin(env: TaskEnv) {
        val data = IntScoreDataContainer(
            PlayerRef::create,
            ordering = Ordering.DESCENDING,
            "score.mobs_killed"
        )

        for (player in env.players) {
            data.identityIfAbsent(player)
        }

        ServerLivingEntityHooks.AFTER_DEATH.registerWith(env.hooks) { _, source ->
            val killer = source.entity as? ServerPlayer ?: return@registerWith

            if (env.players.isParticipating(killer)) {
                data.addScore(killer, 1)
            }
        }

        env.timer("task.$id.task", 30.seconds) {
            env.complete(data)
        }
    }
}