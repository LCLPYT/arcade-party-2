package work.lclpnet.ap2.task_rush.task

import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.ChatFormatting
import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.ext.inWholeTicks
import work.lclpnet.ap2.game.data.OrderedDataContainer
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.kibu.translate.text.FormatWrapper
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val REVEAL_BEFORE = 20.seconds

/**
 * Base class for "be the first to do X" tasks.
 * Players are ranked by the order in which they complete the objective.
 * The task ends early once the order is clear: as soon as everyone but the last player has finished,
 * capped at [winnerCount] (2 players -> 1 finisher, 3 -> 2, 4 or more -> 3). Otherwise it ends when
 * the timer runs out.
 *
 * The timer is hidden until the last [REVEAL_BEFORE] so players are not pressured for most of the round.
 */
abstract class OrderTask(
    override val id: String,
    private val duration: Duration = 90.seconds,
    private val winnerCount: Int = 3,
) : Task {

    protected fun start(env: TaskEnv, onTimeout: () -> Unit = {}): Progress {
        val container = OrderedDataContainer(PlayerRef::create)
        val target = minOf(winnerCount, maxOf(1, env.players.count() - 1))
        val progress = Progress(env, container, target)

        val timer = env.timer("task.$id.task", duration) {
            onTimeout()
            env.complete(container)
        }

        if (duration > REVEAL_BEFORE) {
            timer.bossBar.setVisible(false)
            env.scheduler.timeout((duration - REVEAL_BEFORE).inWholeTicks) { ->
                timer.bossBar.setVisible(true)
            }
        }

        return progress
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

            env.translations.translateText(
                "task.completed_by",
                FormatWrapper.styled(player.scoreboardName, ChatFormatting.YELLOW)
            ).withStyle(ChatFormatting.GREEN).sendTo(PlayerLookup.all(env.level.server))

            if (finishers.size >= target) {
                env.complete(container)
            }
        }

        /**
         * Ranks the given players, in order, after the players who already finished, skipping anyone who
         * has already finished. Used to place the remaining players when the timer runs out.
         */
        fun rankRemaining(players: List<ServerPlayer>) {
            for (player in players) {
                if (player.uuid !in finishers) {
                    container.add(player)
                }
            }
        }
    }
}
