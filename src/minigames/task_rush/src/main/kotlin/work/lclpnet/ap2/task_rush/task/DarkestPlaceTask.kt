package work.lclpnet.ap2.task_rush.task

import net.minecraft.server.level.ServerPlayer
import work.lclpnet.ap2.game.data.IntScoreDataContainer
import work.lclpnet.ap2.game.data.Ordering
import work.lclpnet.ap2.game.data.type.PlayerRef
import work.lclpnet.kibu.hook.level.BlockModificationHooks
import kotlin.time.Duration.Companion.seconds

/**
 * Find the darkest place.
 * Breaking blocks is disabled so players cannot just dig themselves into a hole.
 * The lowest light level at the end wins.
 */
object DarkestPlaceTask : Task {

    override val id = "darkest_place"

    override fun begin(env: TaskEnv) {
        env.duplicateDrops = false

        BlockModificationHooks.BREAK_BLOCK.registerWith(env.hooks) { _, _, entity ->
            entity is ServerPlayer && env.players.isParticipating(entity)
        }

        BlockModificationHooks.PLACE_BLOCK.registerWith(env.hooks) { _, _, entity, _ ->
            entity is ServerPlayer && env.players.isParticipating(entity)
        }

        env.scheduler.interval(10L) { ->
            for (player in env.players) {
                val light = env.level.getMaxLocalRawBrightness(player.blockPosition())
                env.feedback(player, "task.feedback.darkest_place", light)
            }
        }

        env.timer("task.$id.task", 30.seconds) {
            val data = IntScoreDataContainer(PlayerRef::create, Ordering.ASCENDING, "score.light_level")

            for (player in env.players) {
                val light = env.level.getMaxLocalRawBrightness(player.blockPosition())
                data.setScore(player, light)
            }

            env.complete(data)
        }
    }
}
