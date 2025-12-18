package work.lclpnet.ap2.core.hook;

import net.minecraft.world.entity.Mob;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

public interface EntityAfterMoveCallback {

    Hook<EntityAfterMoveCallback> HOOK = HookFactory.createArrayBacked(EntityAfterMoveCallback.class, callbacks -> (entity) -> {
        for (var cb : callbacks) {
            cb.afterMoveTick(entity);
        }
    });

    void afterMoveTick(Mob entity);
}
