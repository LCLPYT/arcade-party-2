package work.lclpnet.ap2.task_rush.task

import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.data.Ordering
import work.lclpnet.ap2.game.data.type.PlayerRef
import kotlin.time.Duration.Companion.seconds

class HeightTask(
    override val id: String,
    val order: Ordering,
) : Task {

    override fun begin(env: TaskEnv) {
        env.timer("task.$id.task", 30.seconds) {
            val data = IntScoreDataContainer(
                PlayerRef::create,
                ordering = order,
                "score.height"
            )

            for (player in env.players) {
                data.setScore(player, player.blockY)
            }

            env.complete(data)
        }
    }
}
