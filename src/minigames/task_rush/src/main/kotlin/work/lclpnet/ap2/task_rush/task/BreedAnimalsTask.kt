package work.lclpnet.ap2.task_rush.task

import work.lclpnet.ap2.core.hook.AnimalBreedCallback
import kotlin.time.Duration.Companion.seconds

/**
 * Be the first to breed two animals.
 */
object BreedAnimalsTask : OrderTask("breed_animals", 60.seconds) {

    override fun begin(env: TaskEnv) {
        val progress = start(env)

        AnimalBreedCallback.HOOK.registerWith(env.hooks) { breeder, _, _, _ ->
            if (env.players.isParticipating(breeder)) {
                progress.finish(breeder)
            }
        }
    }
}
