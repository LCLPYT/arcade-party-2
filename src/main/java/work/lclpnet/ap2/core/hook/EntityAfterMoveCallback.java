package work.lclpnet.ap2.core.hook;

import net.minecraft.entity.mob.MobEntity;
import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;

public interface EntityAfterMoveCallback {

    Hook<EntityAfterMoveCallback> HOOK = HookFactory.createArrayBacked(EntityAfterMoveCallback.class, callbacks -> (entity) -> {
        for (var cb : callbacks) {
            cb.afterMoveTick(entity);
        }
    });

    void afterMoveTick(MobEntity entity);
}
