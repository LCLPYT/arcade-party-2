package work.lclpnet.ap2.task_rush.task

import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.game.data.OrderedDataContainer
import work.lclpnet.ap2.game.data.type.PlayerRef
import java.util.*
import kotlin.time.Duration

/**
 * Base class for "be the first to do X" tasks.
 * Players are ranked by the order in which they complete the objective.
 * The task ends early once the top three (or all participants, if fewer) have finished,
 * otherwise it ends when the timer runs out.
 */
abstract class OrderTask(
    override val id: String,
    private val duration: Duration,
) : Task {

    protected fun start(env: TaskEnv): Progress {
        val container = OrderedDataContainer(PlayerRef::create)
        val target = minOf(3, env.players.count())

        env.timer("task.$id.task", duration) {
            env.complete(container)
        }

        return Progress(env, container, target)
    }

    protected class Progress(
        private val env: TaskEnv,
        private val container: OrderedDataContainer<ServerPlayer, PlayerRef>,
        private val target: Int,
    ) {
        private val finishers = HashSet<UUID>()

        fun finish(player: ServerPlayer) {
            if (!finishers.add(player.uuid)) return

            container.add(player)

            if (finishers.size >= target) {
                env.complete(container)
            }
        }
    }
}
