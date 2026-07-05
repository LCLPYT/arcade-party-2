package work.lclpnet.ap2.task_rush.task

import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.data.Ordering
import work.lclpnet.ap2.game.data.type.PlayerRef
import kotlin.time.Duration.Companion.seconds

class HighestPosTask : Task {

    override val id = "highest_pos"

    override fun begin(env: TaskEnv) {
        env.timer("reach_highest_pos", 30.seconds) {
            val data = IntScoreDataContainer(
                PlayerRef::create,
                ordering = Ordering.DESCENDING,
                "ap2.blocks_high"
            )

            for (player in env.players) {
                data.setScore(player, player.blockY)
            }

            env.complete(data)
        }
    }
}
