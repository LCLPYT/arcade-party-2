package work.lclpnet.ap2.core.hook;

import net.minecraft.world.entity.Entity;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

public interface EntityPushEntityCallback {

    Hook<EntityPushEntityCallback> HOOK = HookFactory.createArrayBacked(EntityPushEntityCallback.class, hooks -> (pushed, pusher) -> {
        boolean allow = true;

        for (var hook : hooks) {
            if (!hook.onPush(pushed, pusher)) {
                allow = false;
            }
        }

        return allow;
    });

    boolean onPush(Entity pushed, Entity pusher);
}
