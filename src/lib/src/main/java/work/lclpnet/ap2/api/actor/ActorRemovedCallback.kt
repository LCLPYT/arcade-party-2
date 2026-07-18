package work.lclpnet.ap2.api.actor

import work.lclpnet.kibu.hook.Hook
import work.lclpnet.kibu.hook.HookFactory
import java.util.function.Function

fun interface ActorRemovedCallback {
    fun onRemoved(actor: Actor)

    companion object {
        @JvmField
        val HOOK: Hook<ActorRemovedCallback> = HookFactory.createArrayBacked(
            ActorRemovedCallback::class.java
        ) { hooks ->
            ActorRemovedCallback { actor ->
                for (hook in hooks) {
                    hook.onRemoved(actor)
                }
            }
        }
    }
}
