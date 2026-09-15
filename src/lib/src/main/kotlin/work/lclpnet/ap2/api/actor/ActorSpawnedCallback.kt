package work.lclpnet.ap2.api.actor

import work.lclpnet.kibu.hook.Hook
import work.lclpnet.kibu.hook.HookFactory

fun interface ActorSpawnedCallback {
    fun onSpawned(actor: Actor)

    companion object {
        @JvmField
        val HOOK: Hook<ActorSpawnedCallback> = HookFactory.createArrayBacked(
            ActorSpawnedCallback::class.java
        ) { hooks ->
            ActorSpawnedCallback { actor ->
                for (hook in hooks) {
                    hook.onSpawned(actor)
                }
            }
        }
    }
}
