package work.lclpnet.ap2.task_rush.task

import net.minecraft.world.level.gamerules.GameRules
import work.lclpnet.kibu.hook.player.PlayerDeathCallback
import kotlin.time.Duration.Companion.seconds

object DieTask : OrderTask("die", 90.seconds) {

    override fun begin(env: TaskEnv) {
        val progress = start(env)

        env.fallDamageDisabled = false
        env.level.gameRules.set(GameRules.NATURAL_HEALTH_REGENERATION, false, env.level.server)

        PlayerDeathCallback.HOOK.registerWith(env.hooks) { player, _ ->
            progress.finish(player)
        }
    }
}