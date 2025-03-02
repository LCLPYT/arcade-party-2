package work.lclpnet.ap2.api.actor;

import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

public interface ActorRemovedCallback {

    Hook<ActorRemovedCallback> HOOK = HookFactory.createArrayBacked(ActorRemovedCallback.class, hooks -> (actor) -> {
        for (var hook : hooks) {
            hook.onRemoved(actor);
        }
    });

    void onRemoved(Actor actor);
}
