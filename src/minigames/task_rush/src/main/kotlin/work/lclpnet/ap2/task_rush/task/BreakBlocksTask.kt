package work.lclpnet.ap2.task_rush.task

import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.data.Ordering
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.kibu.hook.level.BlockModificationHooks
import kotlin.time.Duration.Companion.seconds

/**
 * Break the most blocks.
 */
object BreakBlocksTask : Task {

    override val id = "break_blocks"

    override fun begin(env: TaskEnv) {
        val data = IntScoreDataContainer(PlayerRef::create, Ordering.DESCENDING, "score.blocks_broken")

        for (player in env.players) {
            data.setScore(player, 0)
        }

        BlockModificationHooks.BREAK_BLOCK.registerWith(env.hooks) { level, pos, entity ->
            val breaker = entity as? ServerPlayer

            if (breaker != null && env.players.isParticipating(breaker)) {
                val state = level.getBlockState(pos)
                val destroySpeed = state.getDestroySpeed(level, pos)

                if (destroySpeed > 0) {
                    data.addScore(breaker, 1)
                    env.feedback(breaker, "task.feedback.break_blocks", data.getScore(breaker))
                }
            }

            false
        }

        env.timer("task.$id.task", 30.seconds) {
            env.complete(data)
        }
    }
}
