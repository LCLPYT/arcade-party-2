package work.lclpnet.ap2.api.actor;

import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

public interface ActorSpawnedCallback {

    Hook<ActorSpawnedCallback> HOOK = HookFactory.createArrayBacked(ActorSpawnedCallback.class, hooks -> (actor) -> {
        for (var hook : hooks) {
            hook.onSpawned(actor);
        }
    });

    void onSpawned(Actor actor);
}
